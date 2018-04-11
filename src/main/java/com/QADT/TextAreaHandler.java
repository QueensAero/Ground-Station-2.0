package com.QADT;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import javafx.scene.control.TextArea;

//CLASS TO ADD LOGGING HANDLER TO TEXTAREA
public class TextAreaHandler extends StreamHandler {
    private TextArea textArea = null;
    private int numMessages = 0;

    public void setTextArea(TextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();

        //set max number of messages displayed to 100
        if(++numMessages>100){
        	numMessages = 100;
        	textArea.replaceText(0, textArea.getText().indexOf("\n",textArea.getText().indexOf("\n")+1)+1,"");
        }
        if (textArea != null) {
            textArea.appendText(getFormatter().format(record));
        }
    }

}



