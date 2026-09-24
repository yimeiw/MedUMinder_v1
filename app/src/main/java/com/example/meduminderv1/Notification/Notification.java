package com.example.meduminderv1.Notification;

import com.example.meduminderv1.Model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.List;

public class Notification {
    private String notification_id;
    private String invitation_id;
    private String reference_id;
    private String receiver_uid;
    private String sender_uid;
    private String consumer_uid;
    private String consumer_name;
    private String title;
    private String message;
    private NotificationType type;
    private String target_role;
    private boolean is_read;
    private boolean is_new_schedule;
    private boolean is_deleted;
    private String deleted_item_name;
    private String report_file_path;

    private String snapshot_name;
    private String snapshot_detail;
    private Integer snapshot_stock;

    private String title_key;
    private String message_key;
    private List<String> message_args;
    private List<String> change_items;      // contoh: "times|08:00, 12:00", "stock|30", "end|25/09/2026", "end_removed"
    private Integer snapshot_frequency;
    private List<String> snapshot_times;
    private Timestamp snapshot_at;          // untuk appointment

    public String getTitle_key() { return title_key; }
    public void setTitle_key(String v) { this.title_key = v; }
    public String getMessage_key() { return message_key; }
    public void setMessage_key(String v) { this.message_key = v; }
    public List<String> getMessage_args() { return message_args; }
    public void setMessage_args(List<String> v) { this.message_args = v; }
    public List<String> getChange_items() { return change_items; }
    public void setChange_items(List<String> v) { this.change_items = v; }
    public Integer getSnapshot_frequency() { return snapshot_frequency; }
    public void setSnapshot_frequency(Integer v) { this.snapshot_frequency = v; }
    public List<String> getSnapshot_times() { return snapshot_times; }
    public void setSnapshot_times(List<String> v) { this.snapshot_times = v; }
    public Timestamp getSnapshot_at() { return snapshot_at; }
    public void setSnapshot_at(Timestamp v) { this.snapshot_at = v; }

    public String getSnapshot_name() { return snapshot_name; }
    public void setSnapshot_name(String snapshot_name) { this.snapshot_name = snapshot_name; }
    public String getSnapshot_detail() { return snapshot_detail; }
    public void setSnapshot_detail(String snapshot_detail) { this.snapshot_detail = snapshot_detail; }
    public Integer getSnapshot_stock() { return snapshot_stock; }
    public void setSnapshot_stock(Integer snapshot_stock) { this.snapshot_stock = snapshot_stock; }

    public String getReport_file_path() {
        return report_file_path;
    }

    public void setReport_file_path(String report_file_path) {
        this.report_file_path = report_file_path;
    }

    public boolean isIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(boolean is_deleted) {
        this.is_deleted = is_deleted;
    }

    public String getDeleted_item_name() {
        return deleted_item_name;
    }

    public void setDeleted_item_name(String deleted_item_name) {
        this.deleted_item_name = deleted_item_name;
    }

    private Timestamp created_at, updated_at;
    private Timestamp scheduled_at;

    public Timestamp getScheduled_at() {
        return scheduled_at;
    }
    public void setScheduled_at(Timestamp scheduled_at) {
        this.scheduled_at = scheduled_at;
    }
    public String getNotification_id() {
        return notification_id;
    }
    public void setNotification_id(String notification_id) {
        this.notification_id = notification_id;
    }
    public String getInvitation_id() {
        return invitation_id;
    }

    public void setInvitation_id(String invitation_id) {
        this.invitation_id = invitation_id;
    }

    public String getReference_id() {
        return reference_id;
    }

    public void setReference_id(String reference_id) {
        this.reference_id = reference_id;
    }

    public String getReceiver_uid() {
        return receiver_uid;
    }

    public void setReceiver_uid(String receiver_uid) {
        this.receiver_uid = receiver_uid;
    }

    public String getConsumer_uid() {
        return consumer_uid;
    }

    public void setConsumer_uid(String consumer_uid) {
        this.consumer_uid = consumer_uid;
    }

    public String getConsumer_name() {
        return consumer_name;
    }
    public void setConsumer_name(String consumer_name) {
        this.consumer_name = consumer_name;
    }

    public String getSender_uid() {
        return sender_uid;
    }
    public void setSender_uid(String sender_uid) {
        this.sender_uid = sender_uid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTarget_role() {
        return target_role;
    }

    public void setTarget_role(String target_role) {
        this.target_role = target_role;
    }
    @Exclude
    public UserRole getTargetRoleEnum(){
        if (target_role == null) return null;
        try {
            return UserRole.valueOf(target_role);
        } catch (IllegalArgumentException e){
            return null;
        }
    }

    public boolean isIs_read() {
        return is_read;
    }

    public void setIs_read(boolean is_read) {
        this.is_read = is_read;
    }

    public boolean isIs_new_schedule() {
        return is_new_schedule;
    }

    public void setIs_new_schedule(boolean is_new_schedule) {
        this.is_new_schedule = is_new_schedule;
    }

    public Timestamp getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public Timestamp getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }

    public Notification(){

    }
}