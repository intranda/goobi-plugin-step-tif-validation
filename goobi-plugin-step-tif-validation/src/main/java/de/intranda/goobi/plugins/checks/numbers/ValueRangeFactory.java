package de.intranda.goobi.plugins.checks.numbers;

import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;

public class ValueRangeFactory {

    public static ValueRange create(String s) throws IllegalArgumentException{
        ValueRange range = null;
        if(s.contains(",")) {
            StringTokenizer tokenizer = new StringTokenizer(s, ",");
            range = new ArrayValueRange();
            while(tokenizer.hasMoreTokens()) {
                ((ArrayValueRange)range).addValue(tokenizer.nextToken());
            }
        } else if(s.contains("-")) {
            int index = s.indexOf("-");
            String start = s.substring(0, index);
            String end = s.substring(index+1);
            try {
                double startD = Double.parseDouble(start);
                double endD = Double.parseDouble(end);
                range = new ContinuousArrayRange(startD, endD);
            } catch(NullPointerException | NumberFormatException e) {
                throw new IllegalArgumentException("Cannot parse " + s + " as number range");
            }
        } else if(StringUtils.isNumeric(s)) {
            double value = Double.parseDouble(s);
            range = new SingleValueRange(value);
        } else {
            throw new IllegalArgumentException("Cannot parse " + s + " as number range");
        }
        return range;
    }

}
