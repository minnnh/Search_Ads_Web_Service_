package io.demo.ad;

import java.io.Serializable;
import java.util.List;

public class Ad implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    public int adId;
    public int campaignId;
    public List<String> keyWords;
    public double relevanceScore;
    public double pClick;
    public double bidPrice;
    public double rankScore;
    public double qualityScore;
    public double costPerClick;
    public int position;//1: top , 2: bottom
    public String title; // required
    public double price; // required
    public String thumbnail; // required
    public String description; // required
    public String brand; // required
    public String detail_url; // required
    public String query; //required
    public int query_group_id;
    public String category;
}
