package com.example.meduminderv1.Notification;

import com.example.meduminderv1.Model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

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
    private Timestamp created_at, updated_at;
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