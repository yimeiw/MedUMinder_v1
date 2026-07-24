package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

public class CareRelationship {
    private String relationship_id;
    private String consumer_uid;
    private String caregiver_uid;
    private Timestamp created_at;

    public String getRelationship_id() {
        return relationship_id;
    }

    public void setRelationship_id(String relationship_id) {
        this.relationship_id = relationship_id;
    }

    public String getConsumer_uid() {
        return consumer_uid;
    }

    public void setConsumer_uid(String consumer_uid) {
        this.consumer_uid = consumer_uid;
    }

    public String getCaregiver_uid() {
        return caregiver_uid;
    }

    public void setCaregiver_uid(String caregiver_uid) {
        this.caregiver_uid = caregiver_uid;
    }

    public Timestamp getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public CareRelationship(){

    }
}
