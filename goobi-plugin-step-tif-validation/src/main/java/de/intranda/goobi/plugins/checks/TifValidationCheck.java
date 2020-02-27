package de.intranda.goobi.plugins.checks;

import org.jdom2.Document;

public interface TifValidationCheck {
    public boolean check(Document doc);

    public String getFormattedError(String imageName);
}
