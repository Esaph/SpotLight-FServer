/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import FServerMain.InformationServer;
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;

public class SynchDataFromChat24Hours extends EsaphCommand
{
    private static final String queryGetAllConversationMessagesLast24HoursFromChat = "SELECT * FROM PrivateMoments AS P JOIN PrivateReceivers AS R ON P.Time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) AND P.PPID=R.PPID AND ((R.UID_REC=? AND P.UID=?) OR (R.UID_REC=? AND P.UID=?))";

    private static final String queryGetSaversFromPrivatePost = "SELECT * FROM PrivateMomentsSaved s WHERE s.PPID=?";

    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PPID=?"; // TODO: 17.03.2019 can be optimized for max hashtag count for a single post.


    public SynchDataFromChat24Hours(InformationServer plServer, InformationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }


    @Override
    public void run() throws Exception
    {
        PreparedStatement prGetAktuellePostings = null;
        ResultSet result = null;

        try
        {
            prGetAktuellePostings = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                    .prepareStatement(SynchDataFromChat24Hours.queryGetAllConversationMessagesLast24HoursFromChat);
            prGetAktuellePostings.setLong(1, super.requestHandler().getThreadUID()); //UID
            prGetAktuellePostings.setLong(2, super.requestHandler().getThreadUID()); //UID
            result = prGetAktuellePostings.executeQuery();


            JSONArray jsonArrayChatPostings = new JSONArray();
            JSONObject jsonObjectPost = null;
            JSONArray jsonArrayReceivers = null;


            while(result.next()) //Query found postings.
            {
                if(jsonObjectPost == null || jsonObjectPost.getLong("PPID") != result.getLong("PPID"))
                {
                    //Here is everytime just one receiver, because the post wasnt deleted, the receiver was just removed.
                    if(jsonObjectPost != null)
                    {
                        jsonObjectPost.put("ARR_REC", jsonArrayReceivers);
                        jsonArrayChatPostings.put(jsonObjectPost);
                    }

                    jsonObjectPost = new JSONObject();

                    PreparedStatement checkIfSaved = (PreparedStatement) //Putting saved thinks.
                            super.requestHandler()
                                    .getCurrentConnectionToSql()
                                    .prepareStatement(SynchDataFromChat24Hours.queryGetSaversFromPrivatePost);
                    checkIfSaved.setLong(1, result.getLong("PPID"));

                    ResultSet resultSaved = checkIfSaved.executeQuery();

                    JSONArray jsonArraySaved = new JSONArray();
                    while(resultSaved.next())
                    {
                        if((resultSaved.getLong("UID") == super.requestHandler().getThreadUID() ||
                                resultSaved.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
                        {
                            JSONObject jsonObjectSaveUserInfo = new JSONObject();
                            jsonObjectSaveUserInfo.put("UID_SAVED", resultSaved.getLong("UID_SAVED"));
                            jsonArraySaved.put(jsonObjectSaveUserInfo);
                        }
                    }
                    checkIfSaved.close();
                    resultSaved.close();


                    PreparedStatement prGetHashtagFromPost =
                            (PreparedStatement)
                            super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchDataFromChat24Hours.queryGetHashtagsFromPost);
                    prGetHashtagFromPost.setLong(1, result.getLong("PPID"));
                    ResultSet resultHashtags = prGetHashtagFromPost.executeQuery();
                    JSONArray jsonArrayHashtags = new JSONArray();
                    while(resultHashtags.next())
                    {
                        JSONObject json = new JSONObject();
                        json.put("TAG", resultHashtags.getString("TAG_NAME"));
                        jsonArrayHashtags.put(json);
                    }

                    prGetHashtagFromPost.close();
                    resultHashtags.close();

                    jsonObjectPost.put("ARS", jsonArraySaved);
                    jsonObjectPost.put("ARR_EHT", jsonArrayHashtags);
                    jsonObjectPost.put("PPID", result.getLong("PPID"));
                    jsonObjectPost.put("ABS", result.getLong("UID"));
                    jsonObjectPost.put("PID", result.getString("PID"));
                    jsonObjectPost.put("TYPE", result.getShort("TYPE"));
                    jsonObjectPost.put("DES", result.getString("Beschreibung"));
                    jsonObjectPost.put("TIME", result.getTimestamp("TIME").getTime());

                    jsonArrayReceivers = new JSONArray();
                }

                if(result.getLong("UID") // TODO: 13.03.2019 important rule, should be added into the query.
                        != super.requestHandler().getThreadUID() && result.getLong("UID_REC") != super.requestHandler().getThreadUID())
                    continue; //wenn ich nicht der empänger bin, oder nicht der absender, dann darf ich auch kein receiver außer nur mich in der liste haben.

                JSONObject jsonObjectReceiver = new JSONObject();
                jsonObjectReceiver.put("REC_ID", result.getLong("UID_REC"));
                jsonObjectReceiver.put("ST", (short) result.getShort("State"));
                jsonArrayReceivers.put(jsonObjectReceiver);

                if(result.isLast()) //For the last item we need to insert it at the bottom, not at the top. The while will end end the last post will be missing.
                {
                    jsonObjectPost.put("ARR_REC", jsonArrayReceivers);
                    jsonArrayChatPostings.put(jsonObjectPost);
                }
            }

            if (jsonArrayChatPostings.length() > 0)
            {
                super.requestHandler().getWriter().println("1");
                super.requestHandler().getWriter().println(jsonArrayChatPostings.toString());
            }
            else {
                super.requestHandler().getWriter().println("0");
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("GetAllConversationMessagesLast24Hours() failed: " + ec);
        }
        finally
        {
            if(prGetAktuellePostings != null)
            {
                prGetAktuellePostings.close();
            }

            if(result != null)
            {
                result.close();
            }
        }
    }
}
