package de.intranda.goobi.plugins.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringSubstitutor;
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
    private XPathExpression<Object> xpath;
    private String expectedValue;
    private String errorMessage;
    private Map<String, String> replaceMap;

    // possible values: equals, greater, lesser, exists, not exists, same, multiple
    private String checkType = "equals";
    private String otherXpath;

    public TifValidationSimpleXpathCheck(Set<Namespace> namespaces, String xpath, String expectedValue, String errorMessage) {
        super();
        this.namespaces = namespaces;
        this.xpath = xFactory.compile(xpath, Filters.fpassthrough(), null, namespaces);
        this.expectedValue = expectedValue;
        this.errorMessage = errorMessage;
        this.createReplaceMap();
    }

    public static String validateXPath(String path) {
        if (StringUtils.isNotBlank(path) && !path.matches("\\w+\\(.+\\)")) {
            path = "string(" + path + ")";
        }

        return path;
    }

    private void createReplaceMap() {
        this.replaceMap = new HashMap<>();
        this.replaceMap.put("expected", this.expectedValue);
    }

    @Override
    public boolean check(Document doc) {
        Object value = xpath.evaluateFirst(doc);
        if (value == null) {
            return "not exists".equals(checkType);
        }

        if (value instanceof Element e) {
            value = e.getTextTrim();
        } else if (value instanceof Attribute a) {
            value = a.getValue();
        } else if (value instanceof Text t) {
            value = t.getText();
        } else if (!(value instanceof String)) {
            value = value.toString();
        }
        this.replaceMap.put("found", (String) value);
        String val = (String) value;
        switch (checkType) {
            case "exists":
                return true;
            case "greater":

                if (NumberUtils.isCreatable(val)) {
                    if (val.contains(".")) {
                        double expected = Double.parseDouble(expectedValue);
                        double d = Double.parseDouble(val);
                        return expected <= d;
                    } else {
                        int expected = Integer.parseInt(expectedValue);
                        int i = Integer.parseInt(val);
                        return expected <= i;
                    }
                }
                // no number
                return false;
            case "lesser":
                if (NumberUtils.isCreatable(val)) {
                    if (val.contains(".")) {
                        double expected = Double.parseDouble(expectedValue);
                        double d = Double.parseDouble(val);
                        return expected >= d;
                    } else {
                        int expected = Integer.parseInt(expectedValue);
                        int i = Integer.parseInt(val);
                        return expected >= i;
                    }
                }
                // no number
                return false;
            case "same":
                XPathExpression<Object> o = xFactory.compile(otherXpath, Filters.fpassthrough(), null, namespaces);
                Object otherValue = o.evaluateFirst(doc);
                if (otherValue instanceof Element e) {
                    otherValue = e.getTextTrim();
                } else if (otherValue instanceof Attribute a) {
                    otherValue = a.getValue();
                } else if (otherValue instanceof Text t) {
                    otherValue = t.getText();
                } else if (!(otherValue instanceof String)) {
                    otherValue = otherValue.toString();
                }
                return value.equals(otherValue);
            case "multiple":
                if (NumberUtils.isCreatable(val)) {
                    int expexted = Integer.parseInt(expectedValue);
                    int actual;
                    if (val.contains(".")) {
                        actual = (int) Double.parseDouble(val);
                    } else {
                        actual = Integer.parseInt(val);
                    }
                    return actual % expexted == 0;
                }
                // no number
                return false;
            case "equals": {
                return this.expectedValue.equals(value) || (value instanceof String s && s.matches(this.expectedValue));
            }
            default:
                throw new IllegalArgumentException("Unexpected value: " + checkType);
        }

    }

    @Override
    public String getFormattedError(String imageName) {
        this.replaceMap.put("image", imageName);
        return StringSubstitutor.replace(errorMessage, replaceMap);
    }

}
