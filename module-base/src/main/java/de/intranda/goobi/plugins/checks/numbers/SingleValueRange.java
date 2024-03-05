package de.intranda.goobi.plugins.checks.numbers;

public class SingleValueRange implements ValueRange {

    private Number number = Double.NaN;
    
    public SingleValueRange(Number n) {
        if(n != null) {
            this.number = n;
        }
    }
    
    @Override
    public boolean contains(Number n) {
        return this.number.doubleValue() == n.doubleValue();
    }

}
