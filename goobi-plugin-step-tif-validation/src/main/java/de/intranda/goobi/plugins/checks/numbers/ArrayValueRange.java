package de.intranda.goobi.plugins.checks.numbers;

import java.util.ArrayList;

public class ArrayValueRange implements ValueRange {

    private ArrayList<Number> array = new ArrayList<>();
    
    @Override
    public boolean contains(Number n) {
        for (Number number : array) {
            if(n.doubleValue() == number.doubleValue()) {
                return true;
            }
        }
        return false;
    }

    public void addValue(String value) throws IllegalArgumentException{
        try {            
            Double d = Double.parseDouble(value);
            array.add(d);
        } catch(NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException("Values of an ArrayValueRange must be numbers");
        }
        
    }

}
