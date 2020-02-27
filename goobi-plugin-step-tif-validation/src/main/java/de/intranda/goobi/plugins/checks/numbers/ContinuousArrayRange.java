package de.intranda.goobi.plugins.checks.numbers;

public class ContinuousArrayRange implements ValueRange {

    private Number start = Double.NEGATIVE_INFINITY;
    private Number end = Double.POSITIVE_INFINITY;
    
    public ContinuousArrayRange(Number start, Number end) {
        if(start != null) {            
            this.start = start;
        }
        if(end != null) {
            this.end = end;
        }
    }

    @Override
    public boolean contains(Number n) {
        return start.doubleValue() <= n.doubleValue() && end.doubleValue() >= n.doubleValue();
    }

}
