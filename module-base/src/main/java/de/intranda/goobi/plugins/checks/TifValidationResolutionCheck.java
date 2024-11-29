package de.intranda.goobi.plugins.checks;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.jdom2.Document;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import de.intranda.goobi.plugins.checks.numbers.ValueRange;
import de.intranda.goobi.plugins.checks.numbers.ValueRangeFactory;

public class TifValidationResolutionCheck implements TifValidationCheck {
    public static final String NAME = "resolution_check";

    private static Namespace jhove = Namespace.getNamespace("jhove", "http://hul.harvard.edu/ois/xml/ns/jhove");

    private Namespace mix = Namespace.getNamespace("mix", "http://www.loc.gov/mix/v10");
    private final XPathExpression<Text> exprXnum;
    private final XPathExpression<Text> exprXdenom;
    private final XPathExpression<Text> exprYnum;
    private final XPathExpression<Text> exprYdenom;

    private final String expectedValue;
    private final String errorMessage;
    private Map<String, String> replaceMap;

    public TifValidationResolutionCheck(String expectedValue, String errorMessage, String mixUri) {
        if (mixUri != null) {
            this.mix = Namespace.getNamespace("mix", mixUri);
        }
        this.expectedValue = expectedValue;
        this.errorMessage = errorMessage;

        XPathFactory xFactory = XPathFactory.instance();
        String start =
                "//jhove:repInfo/jhove:properties/jhove:property/jhove:values/jhove:property/jhove:values/jhove:property/jhove:values/jhove:property/jhove:values/jhove:property/jhove:values[@type='NISOImageMetadata']/jhove:value/mix:mix/mix:ImageAssessmentMetadata/mix:SpatialMetrics/";
        start = "//";
        exprXnum = xFactory.compile(start + "mix:xSamplingFrequency/mix:numerator/text()", Filters.text(), null, jhove, mix);
        exprXdenom = xFactory.compile(start + "mix:xSamplingFrequency/mix:denominator/text()", Filters.text(), null, jhove, mix);
        exprYnum = xFactory.compile(start + "mix:ySamplingFrequency/mix:numerator/text()", Filters.text(), null, jhove, mix);
        exprYdenom = xFactory.compile(start + "mix:ySamplingFrequency/mix:denominator/text()", Filters.text(), null, jhove, mix);
        this.createReplaceMap();
    }

    private void createReplaceMap() {
        this.replaceMap = new HashMap<>();
        this.replaceMap.put("wanted", this.expectedValue);
    }

    @Override
    public boolean check(Document doc) {
        Double resolutionXnum = getAsDouble(exprXnum.evaluateFirst(doc));
        Double resolutionXdenom = getAsDouble(exprXdenom.evaluateFirst(doc));
        Double resolutionYnum = getAsDouble(exprYnum.evaluateFirst(doc));
        Double resolutionYdenom = getAsDouble(exprYdenom.evaluateFirst(doc));

        if (resolutionXnum == null || resolutionYnum == null) {
            this.replaceMap.put("found", "--nothing found--");
            return false;
        } else {
            Number compressionValueX = null;
            Number compressionValueY = null;
            if (resolutionXdenom != null && resolutionYdenom != null) {
                long xNum = resolutionXnum.longValue();
                long xDen = resolutionXdenom.longValue();
                compressionValueX = xNum / xDen;
                long yNum = resolutionYnum.longValue();
                long yDen = resolutionYdenom.longValue();
                compressionValueY = yNum / yDen;
            } else {
                compressionValueX = resolutionXnum.intValue();
                compressionValueY = resolutionYnum.intValue();
            }
            ValueRange range = ValueRangeFactory.create(expectedValue);

            if (range.contains(compressionValueX) && range.contains(compressionValueY)) {
                return true;
            } else {
                this.replaceMap.put("found", compressionValueX + "," + compressionValueY);
                return false;
            }
        }
    }

    private Double getAsDouble(Text text) {
        if (text != null) {
            String value = text.getText();
            if (StringUtils.isNotBlank(value) && StringUtils.isNumeric(value)) {
                return Double.valueOf(value);
            }
        }

        return null;

    }

    @Override
    public String getFormattedError(String imageName) {
        this.replaceMap.put("image", imageName);
        return StrSubstitutor.replace(errorMessage, replaceMap);
    }
}
