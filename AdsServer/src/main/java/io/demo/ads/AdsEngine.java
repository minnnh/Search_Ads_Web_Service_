package io.demo.ads;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.json.*;


public class AdsEngine {
	private String mAdsDataFilePath;
	private String mBudgetFilePath;
	String m_logistic_reg_model_file;
	String m_gbdt_model_path;
	private IndexBuilder indexBuilder;
	private String mMemcachedServer;
	private int mMemcachedPortal;
	private int mFeatureMemcachedPortal;
	private int mSynonymsMemcachedPortal;
	private int mTFMemcachedPortal;
	private int mDFMemcachedPortal;
	private String mysql_host;
	private String mysql_db;
	private String mysql_user;
	private String mysql_pass;
	private Boolean enable_query_rewrite;
	private int indexServerTimeout; //ms
	
	public AdsEngine(String adsDataFilePath, String budgetDataFilePath,String logistic_reg_model_file, 
			String gbdt_model_path, String memcachedServer,int memcachedPortal,int featureMemcachedPortal,int synonymsMemcachedPortal,
			int tfMemcachedPortal, int dfMemcachedPortal,
			String mysqlHost,String mysqlDb,String user,String pass)
	{
		mAdsDataFilePath = adsDataFilePath;
		mBudgetFilePath = budgetDataFilePath;
		m_logistic_reg_model_file = logistic_reg_model_file;
		m_gbdt_model_path = gbdt_model_path;
		mMemcachedServer = memcachedServer;
		mMemcachedPortal = memcachedPortal;
		mTFMemcachedPortal = tfMemcachedPortal;
		mDFMemcachedPortal = dfMemcachedPortal;
		mFeatureMemcachedPortal = featureMemcachedPortal;
		mSynonymsMemcachedPortal = synonymsMemcachedPortal;
		mysql_host = mysqlHost;
		mysql_db = mysqlDb;	
		mysql_user = user;
		mysql_pass = pass;	
		enable_query_rewrite = true;
		indexServerTimeout = 50;
		indexBuilder = new IndexBuilder(memcachedServer,memcachedPortal,mysql_host,mysql_db,mysql_user,mysql_pass);
	}
	
	
	public Boolean init() {
		// System.out.println("======= AdsEngine in init() 0 ======");
		//load ads data
		try {
			BufferedReader brAd = new BufferedReader(new FileReader(mAdsDataFilePath));
			// System.out.println("======= AdsEngine in init() 1 ======");
			// if (brAd == null) System.out.println("======= AdsEngine read file failed ======");
			String line;
			while ((line = brAd.readLine()) != null) {
				// System.out.println("======= init ======");
				JSONObject adJson = new JSONObject(line);
				Ad ad = new Ad(); 
				if(adJson.isNull("adId") || adJson.isNull("campaignId")) {
					continue;
				}
				// System.out.println("======= AdsEngine in init() 2======");
				ad.adId = adJson.getLong("adId");
				ad.campaignId = adJson.getLong("campaignId");
				ad.brand = adJson.isNull("brand") ? "" : adJson.getString("brand");
				ad.price = adJson.isNull("price") ? 100.0 : adJson.getDouble("price");
				ad.thumbnail = adJson.isNull("thumbnail") ? "" : adJson.getString("thumbnail");
				ad.title = adJson.isNull("title") ? "" : adJson.getString("title");
				ad.detail_url = adJson.isNull("detail_url") ? "" : adJson.getString("detail_url");						
				ad.bidPrice = adJson.isNull("bidPrice") ? 1.0 : adJson.getDouble("bidPrice");
				ad.pClick = adJson.isNull("pClick") ? 0.0 : adJson.getDouble("pClick");
				ad.category =  adJson.isNull("category") ? "" : adJson.getString("category");
				ad.description = adJson.isNull("description") ? "" : adJson.getString("description");
				ad.keyWords = new ArrayList<String>();
				JSONArray keyWords = adJson.isNull("keyWords") ? null :  adJson.getJSONArray("keyWords");
				for(int j = 0; j < keyWords.length();j++)
				{
					ad.keyWords.add(keyWords.getString(j));
				}
//				indexBuilder.buildInvertIndex(ad);
//				indexBuilder.buildForwardIndex(ad);
				if(!indexBuilder.buildInvertIndex(ad) || !indexBuilder.buildForwardIndex(ad))
				{
					//log	
//					System.out.println("cache added ad data failed");
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//load budget data
		try {
			BufferedReader brBudget = new BufferedReader(new FileReader(mBudgetFilePath));
			String line;
			while ((line = brBudget.readLine()) != null) {
				JSONObject campaignJson = new JSONObject(line);
				Long campaignId = campaignJson.getLong("campaignId");
				double budget = campaignJson.getDouble("budget");
				Campaign camp = new Campaign();
				camp.campaignId = campaignId;
				camp.budget = budget;
//				if(!indexBuilder.updateBudget(camp))
//				{
//					//log
//					System.out.println("cache added camp data failed");
//				}			
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// indexBuilder.Close();
		// System.out.println("=======ads engine init done==========");
		return true;
	}
	
	public List<Ad> selectAds(String query, String device_id, String device_ip, String query_category)
	{
		//query understanding
		//raw query: nike running shoe
		//running == jogging
		//nike running shoe => nike jogging shoe	
		List<Ad> adsCandidates = new ArrayList<Ad>();
		
		if (enable_query_rewrite) {
			//get rewrite from offline
			List<List<String>> rewrittenQuery =  QueryParser.getInstance().OfflineQueryRewrite(query, mMemcachedServer, mSynonymsMemcachedPortal);	
			System.out.println(rewrittenQuery.size() + "==== start online rewrite =====");
			//get rewrite from online
			if (rewrittenQuery.size() != 0) {
			// if (rewrittenQuery.size() == 0) {
				System.out.println("==== in start online rewrite =====");
				List<String> queryTermList = QueryParser.getInstance().QueryUnderstand(query);
				//set timeout for OnlineQueryRewrite
				//estimation (low cost: n-gram, lookup):
				//how much % bi-gram we have exiting rewrite query, if % < 40, no OnlineQueryRewrite call
				rewrittenQuery =  QueryParser.getInstance().OnlineQueryRewrite(queryTermList, mMemcachedServer, mSynonymsMemcachedPortal);	
				System.out.println("==== start online rewrite =====");
			}
			
			
			Set<Long> uniquueAds = new HashSet<Long>();		
			//select ads candidates for each rewritten Query
			//TODO: mult-thread call for each rewritten Query
			for (List<String> queryTerms : rewrittenQuery) {	
				System.out.println("AdsEngine rewrite queryTerms: " + queryTerms);
				List<Ad> adsCandidates_temp = AdsSelector.getInstance(mMemcachedServer, mMemcachedPortal,mFeatureMemcachedPortal,mTFMemcachedPortal,mDFMemcachedPortal,
						m_logistic_reg_model_file,m_gbdt_model_path, mysql_host, mysql_db,mysql_user, mysql_pass).selectAds(queryTerms,device_id, device_ip, query_category);	
				for(Ad ad : adsCandidates_temp) {
					if (!uniquueAds.contains(ad.adId)) {
						adsCandidates.add(ad);
					}
				}
			}
			
		
		} else {
			List<String> queryTerms = QueryParser.getInstance().QueryUnderstand(query);


			adsCandidates = AdsSelector.getInstance(mMemcachedServer, mMemcachedPortal,mFeatureMemcachedPortal,mTFMemcachedPortal, mDFMemcachedPortal,
				m_logistic_reg_model_file,m_gbdt_model_path, mysql_host, mysql_db,mysql_user, mysql_pass).selectAds(queryTerms,device_id, device_ip, query_category);			
		
			System.out.println("======== enable_query_rewrite = false ========");
		}
		
		
		
		
		//rank 
		List<Ad> rankedAds = AdsRanker.getInstance().rankAds(adsCandidates);
		System.out.println("rankedAds ads left = " + rankedAds.size());

		//L1 filter by relevance score : select top K ads
		int k = 50;
		List<Ad> unfilteredAds = AdsFilter.getInstance().LevelOneFilterAds(rankedAds,k);
		System.out.println("unfilteredAds ads left = " + unfilteredAds.size());

		//Dedupe ads per campaign
		List<Ad> dedupedAds = AdsCampaignManager.getInstance(mysql_host, mysql_db,mysql_user, mysql_pass).DedupeByCampaignId(unfilteredAds);
	    System.out.println("dedupedAds ads left = " + dedupedAds.size());

		//pricing： next rank score/current score * current bid price
		AdPricing.getInstance().setCostPerClick(dedupedAds);
		//filter last one , ad without budget , ads with CPC < minReservePrice
		List<Ad> ads = AdsCampaignManager.getInstance(mysql_host, mysql_db,mysql_user, mysql_pass).ApplyBudget(dedupedAds);
		System.out.println("AdsCampaignManager ads left = " + ads.size());

		//allocation
		AdsAllocation.getInstance().AllocateAds(ads);
		return ads;
	}
}
