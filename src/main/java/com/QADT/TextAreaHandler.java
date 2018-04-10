package com.QADT;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import javafx.scene.control.TextArea;

//CLASS TO ADD LOGGING HANDLER TO TEXTAREA
public class TextAreaHandler extends StreamHandler {
    private TextArea textArea = null;

    public void setTextArea(TextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();

        if (textArea != null) {
            textArea.appendText(getFormatter().format(record));
        }
    }
}



