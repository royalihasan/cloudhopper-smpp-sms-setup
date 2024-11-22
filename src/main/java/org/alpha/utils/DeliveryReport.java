package org.alpha.utils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DeliveryReport implements Serializable {
    private final String messageId;  // Immutable
    private int submittedParts;
    private int deliveredParts;
    private LocalDateTime submitDate;
    private LocalDateTime doneDate;
    private DeliveryStatus status;
    private int errorCode;

    // Constructor with messageId and status
    public DeliveryReport(String messageId, DeliveryStatus status) {
        this.messageId = messageId;
        this.status = status;
        this.submitDate = LocalDateTime.now();
        this.doneDate = LocalDateTime.now();
    }

    // Full constructor
    public DeliveryReport(String messageId, int submittedParts, int deliveredParts,
                          LocalDateTime submitDate, LocalDateTime doneDate,
                          DeliveryStatus status, int errorCode) {
        this.messageId = messageId;
        this.submittedParts = submittedParts;
        this.deliveredParts = deliveredParts;
        this.submitDate = submitDate;
        this.doneDate = doneDate;
        this.status = status;
        this.errorCode = errorCode;
    }

    // Getters and setters
    public String getMessageId() {
        return messageId;
    }

    public int getSubmittedParts() {
        return submittedParts;
    }

    public void setSubmittedParts(int submittedParts) {
        this.submittedParts = submittedParts;
    }

    public int getDeliveredParts() {
        return deliveredParts;
    }

    public void setDeliveredParts(int deliveredParts) {
        this.deliveredParts = deliveredParts;
    }

    public LocalDateTime getSubmitDate() {
        return submitDate;
    }

    public void setSubmitDate(LocalDateTime submitDate) {
        this.submitDate = submitDate;
    }

    public LocalDateTime getDoneDate() {
        return doneDate;
    }

    public void setDoneDate(LocalDateTime doneDate) {
        this.doneDate = doneDate;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    // Convenience method to get description of the status
    public String getStatusDescription() {
        return status.getDescription();
    }

    // Enum for DeliveryStatus with descriptions
    public enum DeliveryStatus {
        DELIVRD("Delivered"),
        EXPIRED("Message Expired"),
        DELETED("Message Deleted"),
        UNDELIV("Undeliverable"),
        ACCEPTED("Accepted"),
        UNKNOWN("Unknown Status");

        private final String description;

        DeliveryStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Override toString to provide formatted date output
    // Convert DLR object to a serializable string format
    private String convertDlrToString(DeliveryReport dlr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

        return String.format("id:%s sub:%03d dlvrd:%03d submit date:%s done date:%s stat:%s err:%03d",
                dlr.getMessageId(),
                dlr.getSubmittedParts(),
                dlr.getDeliveredParts(),
                dlr.getSubmitDate().format(formatter),
                dlr.getDoneDate().format(formatter),
                dlr.getStatus().getDescription(),
                dlr.getErrorCode()
        );
    }

}
