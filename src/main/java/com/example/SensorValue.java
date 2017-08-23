package com.example;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SensorValue {
    private Date timestamp;
    private double lat;
    private double lng;
    private int sensorId;
    private double p1;
    private double p2;

    public String timestampString;

    public SensorValue() {
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            this.timestamp = formatter.parse(timestamp);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }


    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public int getSensorId() {
        return sensorId;
    }

    public void setSensorId(int sensorId) {
        this.sensorId = sensorId;
    }

    public double getP1() {
        return p1;
    }

    public void setP1(double p1) {
        this.p1 = p1;
    }

    public double getP2() {
        return p2;
    }

    public void setP2(double p2) {
        this.p2 = p2;
    }

    public Location getLocation() {
        Location location = new Location("Point", this.getLat(), this.getLng());
        return location;
    }


}