package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.jdom2.Namespace;

import de.intranda.goobi.plugins.checks.TifValidationCheck;
import de.intranda.goobi.plugins.checks.TifValidationResolutionCheck;
import de.intranda.goobi.plugins.checks.TifValidationSimpleXpathCheck;
import lombok.Getter;

public class TifValidationConfiguration {
    private static final Pattern nsPattern = Pattern.compile("([\\w]+):(?!:)");

    private final SubnodeConfiguration config;

    @Getter
    private ArrayList<TifValidationCheck> checks;
    @Getter
    private Map<String, Namespace> namespaces;

    @Getter
    private String jhoveConfigurationFile;
    @Getter
    private String stepToOpenInCaseOfErrors;
    @Getter
    private List<String> folders = null;
    
    @Getter
    private boolean lockStepsBetweenCurrentStepAndErrorStep;

    public TifValidationConfiguration(SubnodeConfiguration config) {
        this.config = config;

        readNamespaces();
        readChecks();
    }

    private void readChecks() {
        this.checks = new ArrayList<>();
        List<HierarchicalConfiguration> checkList = config.configurationsAt("/check");
        for (HierarchicalConfiguration hc : checkList) {
            String xpath = hc.getString("xpath");
            Set<Namespace> nsSet = new HashSet<>();
            Matcher matcher = nsPattern.matcher(xpath);
            while (matcher.find()) {
                String nsName = matcher.group(1);
                Namespace ns = namespaces.get(nsName);
                if (ns == null) {
                    return;
                }
                nsSet.add(ns);
            }
            String wanted = hc.getString("wanted");
            String errorMessage = hc.getString("error_message");
            this.checks.add(new TifValidationSimpleXpathCheck(nsSet, xpath, wanted, errorMessage));
        }

        //ADD NEW CHECKS HERE
        checkList = config.configurationsAt("/integrated_check");
        for (HierarchicalConfiguration hc : checkList) {
            String type = hc.getString("@name");
            if (TifValidationResolutionCheck.NAME.equals(type)) {
                String wanted = hc.getString("wanted");
                String errorMessage = hc.getString("error_message");
                String mixUri = hc.getString("mix_uri");
                this.checks.add(new TifValidationResolutionCheck(wanted, errorMessage, mixUri));
            }
        }

        jhoveConfigurationFile = config.getString("jhoveConfiguration", "/opt/digiverso/goobi/config/jhove/jhove.conf");
        stepToOpenInCaseOfErrors = config.getString("openStepOnError", null);
        folders = Arrays.asList(config.getStringArray("folder"));
        lockStepsBetweenCurrentStepAndErrorStep = config.getBoolean("lockAllStepsBetween");
    }

    private void readNamespaces() {
        this.namespaces = new HashMap<>();
        List<HierarchicalConfiguration> nsList = config.configurationsAt("/namespace");
        for (HierarchicalConfiguration hc : nsList) {
            String name = hc.getString("@name");
            String uri = hc.getString("@uri");
            this.namespaces.put(name, Namespace.getNamespace(name, uri));
        }

    }
}
