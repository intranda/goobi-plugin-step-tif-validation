package de.intranda.goobi.plugins.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import lombok.Data;

@Data
public class TifValidationSimpleXpathCheck implements TifValidationCheck {
    private static XPathFactory xFactory = XPathFactory.instance();

    private Set<Namespace> namespaces;
    private XPathExpression xpath;
    private String expectedValue;
    private String errorMessage;
    private Map<String, String> replaceMap;

    public TifValidationSimpleXpathCheck(Set<Namespace> namespaces, String xpath, String expectedValue, String errorMessage) {
        super();
        this.namespaces = namespaces;
        this.xpath = xFactory.compile(xpath, Filters.fpassthrough(), null, namespaces);
        this.expectedValue = expectedValue;
        this.errorMessage = errorMessage;
        this.createReplaceMap();
    }

    public static String validateXPath(String path) {
        if (StringUtils.isNotBlank(path)) {
            if (!path.matches("\\w+\\(.+\\)")) {
                path = "string(" + path + ")";
            }
        }
        return path;
    }

    private void createReplaceMap() {
        this.replaceMap = new HashMap<>();
        this.replaceMap.put("wanted", this.expectedValue);
    }

    @Override
    public boolean check(Document doc) {
        Object value = xpath.evaluateFirst(doc);
        if (value instanceof Element) {
            value = ((Element) value).getTextTrim();
        } else if (value instanceof Attribute) {
            value = ((Attribute) value).getValue();
        } else if (value instanceof Text) {
            value = ((Text) value).getText();
        } else if (!(value instanceof String)) {
            value = value.toString();
        }
        this.replaceMap.put("found", (String) value);
        return this.expectedValue.equals(value) || (value != null && value instanceof String && ((String) value).matches(this.expectedValue));
        //        return this.expectedValue.equals(value);
    }

    @Override
    public String getFormattedError(String imageName) {
        this.replaceMap.put("image", imageName);
        return StrSubstitutor.replace(errorMessage, replaceMap);
    }

}
