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
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

import de.intranda.goobi.plugins.checks.TifValidationCheck;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.ProcessManager;
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

    private static final Namespace jhove = Namespace.getNamespace("jhove", "http://hul.harvard.edu/ois/xml/ns/jhove");

    private TifValidationConfiguration configuration;

    @Override
    public PluginReturnValue run() {
        // get folder list
        try {
            Calendar calendar = Calendar.getInstance();
            App app = new App(TifValidationPlugin.class.getSimpleName(), "1.0",
                    new int[] { calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH) }, "jHove", "");

            JhoveBase jhoveBase = new JhoveBase();

            File jhoveConfigFile =
                    new File("/home/robert/git/goobi-plugin-step-tif-validation/goobi-plugin-step-tif-validation/src/main/resources/jhove.conf");
            jhoveBase.init(jhoveConfigFile.getAbsolutePath(), null);

            Path outputPath = Files.createTempDirectory("jhove");
            List<SimpleEntry<String, String>> inputOutputList = new ArrayList<>();

            //            File outputFile = File.createTempFile("jhove", "output");
            Module module = jhoveBase.getModule(null);
            OutputHandler aboutHandler = jhoveBase.getHandler(null);
            OutputHandler xmlHandler = jhoveBase.getHandler("XML");

            jhoveBase.setEncoding("utf-8");
            //            jhoveBase.setTempDirectory("/tmp");
            jhoveBase.setBufferSize(4096);
            jhoveBase.setChecksumFlag(false);
            jhoveBase.setShowRawFlag(true);
            jhoveBase.setSignatureFlag(false);

            List<Path> imagesInFolder = StorageProvider.getInstance().listFiles("/opt/digiverso/goobi/metadata/34696/images/808840800_media/");
            List<String> filenameList = new ArrayList<>(imagesInFolder.size());
            for (Path image : imagesInFolder) {
                filenameList.add(image.toString());
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
                    return PluginReturnValue.ERROR;
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
                    return PluginReturnValue.ERROR;
                }
            }
            if (error) {
                String errorMessage = "One or more images did not validate";
                handleValidationError(errorMessage);
                return PluginReturnValue.ERROR;
            }

        } catch (Exception e) {
            // TODO Auto-generated catch block
            log.error(e);
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

    private void handleValidationError(String message) {

        // TODO abort, open previous/named step in error state, write error message

    }

    private void handleException(Exception e) {
        log.error(e);
        handleValidationError(e.getMessage());
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
        run();
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
