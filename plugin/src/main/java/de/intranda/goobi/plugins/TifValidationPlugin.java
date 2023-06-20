package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.ErrorProperty;
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
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.StepManager;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.Fileformat;
import ugh.exceptions.UGHException;

@Log4j2
@PluginImplementation
public class TifValidationPlugin implements IStepPluginVersion2 {

    private static final long serialVersionUID = 7056713235306414554L;

    @Getter
    private String title = "intranda_step_tif_validation";

    private Step step;
    private Process process;
    private String returnPath;

    private transient TifValidationConfiguration configuration;

    @Override
    public PluginReturnValue run() {

        if (configuration.getFolders() == null || configuration.getFolders().isEmpty()) {
            // nothing to validate, continue

            Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "No folder configured to be validated with TIF validation.", "");

            return PluginReturnValue.ERROR;
        }

        // get folder list
        try {
            Calendar calendar = Calendar.getInstance();
            App app = new App(TifValidationPlugin.class.getSimpleName(), "1.0",
                    new int[] { calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH) }, "jHove", "");

            JhoveBase jhoveBase = new JhoveBase();

            File jhoveConfigFile = new File(configuration.getJhoveConfigurationFile());

            jhoveBase.init(jhoveConfigFile.getAbsolutePath(), null);
            Path outputPath = Paths.get(process.getProcessDataDirectory(), "validation", System.currentTimeMillis() + "_jhove");
            Files.createDirectories(outputPath);

            List<SimpleEntry<String, String>> inputOutputList = new ArrayList<>();

            Module module = jhoveBase.getModule(null);
            OutputHandler xmlHandler = jhoveBase.getHandler("XML");

            jhoveBase.setEncoding("utf-8");
            jhoveBase.setBufferSize(4096);
            jhoveBase.setChecksumFlag(false);
            jhoveBase.setShowRawFlag(true);
            jhoveBase.setSignatureFlag(false);
            List<Path> imagesInFolder = new ArrayList<>();

            for (String f : configuration.getFolders()) {
                imagesInFolder.addAll(StorageProvider.getInstance().listFiles(process.getConfiguredImageFolder(f), NIOFileUtils.imageNameFilter));
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

            SAXBuilder jdomBuilder = XmlTools.getSAXBuilder();
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
                String errorMessage = "The validation of the images was not successfull.";
                handleValidationError(errorMessage);
                openConfiguredTask( errorMessage);
                return PluginReturnValue.WAIT;
            }
        } catch (Exception e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        Helper.setMeldung("Tif validation finished.");
        Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Tif validation finished.", "");

        return PluginReturnValue.FINISH;
    }

    private void openConfiguredTask(String errorMessage) throws DAOException {
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
        ErrorProperty se = new ErrorProperty();
        se.setTitel(Helper.getTranslation("Korrektur notwendig"));
        se.setWert(errorMessage);
        se.setType(PropertyType.MESSAGE_ERROR);
        // found no step to lockse, set status of the current step to error
        if (stepToOpen == null) {
            step.setBearbeitungsstatusEnum(StepStatus.ERROR);
            se.setSchritt(step);
            step.getEigenschaften().add(se);
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
                    stepBetween.setPrioritaet(10);
                    StepManager.saveStep(stepBetween);
                }
            }

            // set the current step to locked, open the previous step
            step.setBearbeitungsstatusEnum(StepStatus.LOCKED);
            stepToOpen.setBearbeitungsstatusEnum(StepStatus.ERROR);
            stepToOpen.setPrioritaet(10);
            se.setSchritt(stepToOpen);
            stepToOpen.getEigenschaften().add(se);
            StepManager.saveStep(stepToOpen);

            Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Open task " + stepToOpen.getTitel() + " because of validation errors.",
                    "");

        }

        step.setEditTypeEnum(StepEditType.MANUAL_SINGLE);
        StepManager.saveStep(step);
    }

    private void handleValidationError(String message) {
        Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, message, "");
    }

    private void handleException(Exception e) {
        log.error(e);
        Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR, "The image validation failed with an error. " + e.getMessage(), "");
    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public boolean execute() {
        return PluginReturnValue.FINISH.equals(run());
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
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(getTitle(), step);

        Fileformat fileformat = null;
        DigitalDocument dd = null;
        try {
            fileformat = process.readMetadataFile();
            dd = fileformat.getDigitalDocument();
        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
        }

        VariableReplacer replacer = new VariableReplacer(dd, process.getRegelsatz().getPreferences(), process, step);

        configuration = new TifValidationConfiguration(myconfig, replacer);
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null; // NOSONAR
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
