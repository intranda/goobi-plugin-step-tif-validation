package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.LogEntry;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import de.intranda.goobi.plugins.checks.TifValidationCheck;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.StepManager;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@Log4j2
@PluginImplementation
public class TifValidationPlugin implements IStepPluginVersion2 {

    private Step step;
    private Process process;
    private String returnPath;

    private TifValidationConfiguration configuration;

    @Override
    public PluginReturnValue run() {

        if (!configuration.isValidateMasterFolder() && !configuration.isValidateMediaFolder()) {
            // nothing to validate, continue
            LogEntry entry = LogEntry.build(process.getId())
                    .withCreationDate(new Date())
                    .withType(LogType.DEBUG)
                    .withUsername("")
                    .withContent("Validation is deactivated for master and media folder.");
            ProcessManager.saveLogEntry(entry);

            return PluginReturnValue.FINISH;
        }

        // get folder list
        try {
            Calendar calendar = Calendar.getInstance();
            App app = new App(TifValidationPlugin.class.getSimpleName(), "1.0",
                    new int[] { calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH) }, "jHove", "");

            JhoveBase jhoveBase = new JhoveBase();

            File jhoveConfigFile = new File(configuration.getJhoveConfigurationFile());

            jhoveBase.init(jhoveConfigFile.getAbsolutePath(), null);
            //            Path template = Paths.get(ConfigProjectsTest.class.getClassLoader().getResource(".").getFile());
            //            String goobiFolder = template.getParent().getParent().getParent().toString() + "/test/resources/";
            Path outputPath = Files.createTempDirectory("jhove");
            List<SimpleEntry<String, String>> inputOutputList = new ArrayList<>();

            Module module = jhoveBase.getModule(null);
            OutputHandler xmlHandler = jhoveBase.getHandler("XML");

            jhoveBase.setEncoding("utf-8");
            jhoveBase.setBufferSize(4096);
            jhoveBase.setChecksumFlag(false);
            jhoveBase.setShowRawFlag(true);
            jhoveBase.setSignatureFlag(false);
            List<Path> imagesInFolder = new ArrayList<>();

            if (configuration.isValidateMasterFolder()) {
                imagesInFolder.addAll(StorageProvider.getInstance().listFiles(process.getImagesOrigDirectory(false)));
            }
            if (configuration.isValidateMediaFolder()) {
                imagesInFolder.addAll(StorageProvider.getInstance().listFiles(process.getImagesTifDirectory(false)));
            }

            for (Path image : imagesInFolder) {
                String inputName = image.getFileName().toString();
                String outputName = inputName.substring(0, inputName.lastIndexOf('.')) + ".xml";
                Path fOutputPath = outputPath.resolve(outputName);
                inputOutputList.add(new SimpleEntry<>(image.toString(), fOutputPath.toString()));
            }

            for (SimpleEntry<String, String> se : inputOutputList) {
                try {
                    jhoveBase.dispatch(app, module, null, xmlHandler, se.getValue(), new String[] { se.getKey() });
                } catch (Exception e) {
                    handleException(e);
                    return PluginReturnValue.WAIT;
                }
            }

            SAXBuilder jdomBuilder = new SAXBuilder();
            Document jdomDocument;
            boolean error = false;

            List<TifValidationCheck> checks = configuration.getChecks();
            for (SimpleEntry<String, String> se : inputOutputList) {
                try {
                    jdomDocument = jdomBuilder.build(se.getValue());

                    for (TifValidationCheck check : checks) {
                        if (!check.check(jdomDocument)) {
                            error = true;

                            handleValidationError(check.getFormattedError(se.getKey()));

                        }
                    }
                } catch (JDOMException | IOException | IllegalStateException e) {
                    handleException(e);
                    return PluginReturnValue.WAIT;
                }
            }
            if (error) {
                String errorMessage = "One or more images did not validate.";
                handleValidationError(errorMessage);
                openConfiguredTask(outputPath);
                return PluginReturnValue.WAIT;
            }

            StorageProvider.getInstance().deleteDir(outputPath);
        } catch (Exception e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        Helper.setMeldung("Tif validation finished.");
        LogEntry entry = LogEntry.build(process.getId())
                .withCreationDate(new Date())
                .withType(LogType.DEBUG)
                .withUsername("")
                .withContent("Tif validation finished.");
        ProcessManager.saveLogEntry(entry);

        return PluginReturnValue.FINISH;
    }

    private void openConfiguredTask(Path outputFolder) throws DAOException {

        StorageProvider.getInstance().deleteDir(outputFolder);

        Step stepToOpen = null;
        // find configured step
        if (StringUtils.isNotBlank(configuration.getStepToOpenInCaseOfErrors())) {
            for (Step stepInProcess : process.getSchritte()) {
                if (stepInProcess.getTitel().equalsIgnoreCase(configuration.getStepToOpenInCaseOfErrors())) {
                    stepToOpen = stepInProcess;
                    break;
                }
            }
        }
        // if step not found or was not configured, find last closed step
        if (stepToOpen == null) {
            for (Step stepInProcess : process.getSchritte()) {
                if (stepInProcess.getTitel().equals(step.getTitel())) {
                    break;
                }
                if (stepToOpen == null || stepToOpen.getReihenfolge() <= stepInProcess.getReihenfolge()) {
                    stepToOpen = stepInProcess;
                }
            }
        }




        // found no step to lockse, set status of the current step to error
        if (stepToOpen == null) {
            step.setBearbeitungsstatusEnum(StepStatus.ERROR);
        } else {
            // close steps between step to open and current step
            if (configuration.isLockStepsBetweenCurrentStepAndErrorStep()) {
                List<Step> stepsToClose = new ArrayList<>();
                for (Step stepInProcess : process.getSchritte()) {
                    if (stepInProcess.getReihenfolge() > stepToOpen.getReihenfolge() && stepInProcess.getReihenfolge() <= step.getReihenfolge()) {
                        stepsToClose.add(stepInProcess);
                    }
                }
                for (Step stepBetween : stepsToClose) {
                    stepBetween.setBearbeitungsstatusEnum(StepStatus.LOCKED);
                    StepManager.saveStep(stepBetween);
                }

            }

            // set the current step to locked, open the previous step
            step.setBearbeitungsstatusEnum(StepStatus.LOCKED);
            stepToOpen.setBearbeitungsstatusEnum(StepStatus.OPEN);
            StepManager.saveStep(stepToOpen);
            LogEntry entry = LogEntry.build(process.getId())
                    .withCreationDate(new Date())
                    .withType(LogType.DEBUG)
                    .withUsername("")
                    .withContent("Open task " + stepToOpen.getTitel() + " because of validation errors.");
            ProcessManager.saveLogEntry(entry);
        }

        step.setEditTypeEnum(StepEditType.MANUAL_SINGLE);
        StepManager.saveStep(step);

    }

    private void handleValidationError(String message) {

        LogEntry entry = LogEntry.build(process.getId()).withCreationDate(new Date()).withType(LogType.ERROR).withUsername("").withContent(message);
        ProcessManager.saveLogEntry(entry);

    }

    private void handleException(Exception e) {
        log.error(e);

        LogEntry entry = LogEntry.build(process.getId())
                .withCreationDate(new Date())
                .withType(LogType.ERROR)
                .withUsername("")
                .withContent("Tif validation failed with an error. " + e.getMessage());
        ProcessManager.saveLogEntry(entry);

    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public boolean execute() {
        if (run().equals(PluginReturnValue.FINISH)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public Step getStep() {
        return step;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        this.process = step.getProzess();
        this.returnPath = returnPath;

        String projectName = process.getProjekt().getTitel();

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(getTitle());
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;

        // order of configuration is:
        // 1.) project name and step name matches
        // 2.) step name matches and project is *
        // 3.) project name matches and step name is *
        // 4.) project name and step name are *
        try {
            myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
        } catch (IllegalArgumentException e) {
            try {
                myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '" + step.getTitel() + "']");
            } catch (IllegalArgumentException e1) {
                try {
                    myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '*']");
                } catch (IllegalArgumentException e2) {
                    myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
                }
            }
        }
        configuration = new TifValidationConfiguration(myconfig);
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public String getTitle() {
        return "intranda_step_jhove-validation";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }
}
