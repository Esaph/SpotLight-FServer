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

@Deprecated
public class SynchAllPostsFromAllUsers extends EsaphCommand
{
    private static final String queryGetAllConversation = "SELECT * FROM PrivateMoments WHERE ( PrivateMoments.FUID = ? OR PrivateMoments.UID = ? ) AND EXISTS ( SELECT NULL FROM Watcher WHERE (( (PrivateMoments.UID = Watcher.UID AND PrivateMoments.FUID = Watcher.FUID) AND ( Watcher.AD = 0 AND Watcher.WF = 0 ) ) OR ( (PrivateMoments.UID = Watcher.FUID AND PrivateMoments.FUID= Watcher.UID) AND ( Watcher.AD = 0 AND Watcher.WF = 0 ) ) ) ) OR ( PrivateMoments.UID = ?) GROUP BY PrivateMoments.PID ORDER BY PrivateMoments.Time DESC LIMIT ?, 30";
    private static final String queryGetAllReceivers = "SELECT FUID, State FROM PrivateMoments WHERE PID=? AND UID=?"; //Will not take care of performence.

    private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PID=?";
    private static final String queryLookUpIfPostSaved = "SELECT * FROM PrivateMomentsSaved WHERE PID=?";

    public SynchAllPostsFromAllUsers(InformationServer plServer, InformationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }


    private JSONArray getAllReceiversFromPost(String PID) throws Exception //Only my post
    {
        JSONArray jsonArray = new JSONArray();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try
        {
            preparedStatement = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPostsFromAllUsers.queryGetAllReceivers);
            preparedStatement.setString(1, PID);
            preparedStatement.setLong(2, super.requestHandler().getThreadUID());
            resultSet = preparedStatement.executeQuery();
            while(resultSet.next())
            {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("REC", resultSet.getLong("FUID"));
                jsonObject.put("ST", resultSet.getShort("STATUS"));
                jsonArray.put(jsonObject);
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("getAllReceiversFromPost() failed: " + ec);
        }
        finally
        {
            if(preparedStatement != null)
            {
                preparedStatement.close();
            }

            if(resultSet != null)
            {
                resultSet.close();
            }
        }
        return jsonArray;
    }


    @Override
    public void run() throws Exception
    {
        JSONArray jsonArrayLastConversationMessages = new JSONArray();

        PreparedStatement prLastMessage = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                .prepareStatement(SynchAllPostsFromAllUsers.queryGetAllConversation);
        prLastMessage.setLong(1, super.requestHandler().getThreadUID());
        prLastMessage.setLong(2, super.requestHandler().getThreadUID());
        prLastMessage.setLong(3, super.requestHandler().getThreadUID());
        prLastMessage.setLong(4, super.requestHandler().getJSONMessage().getInt("C"));

        ResultSet resultLastMessage = prLastMessage.executeQuery();
        while (resultLastMessage.next())
                //&& !(resultLastMessage.getLong("FUID") == super.requestHandler().getThreadUID()
                //&& resultLastMessage.getShort("STATUS") == 1) I removed it, because message that was received, but not openedn. Get lost in this system.
                //Because new messages, which where sent to me, were stored in unreceived messages. Its preventing that we get 2 messages, or the message is
                //getting updated. (Message_Status could might change!) And the problem is that by this solution, maybe a post get lost.
        // )) // Der absender
        // darf nicht in
        // der liste
        // sein.
        {
            JSONObject lastConversationMessage = new JSONObject();
            if(super.requestHandler().getThreadUID() == resultLastMessage.getLong("UID"))
            {
                lastConversationMessage.put("ARR_REC", getAllReceiversFromPost(resultLastMessage.getString("PID")));
            }

            lastConversationMessage.put("PPID", resultLastMessage.getLong("PPID"));
            lastConversationMessage.put("ABS", (resultLastMessage.getLong("UID")));
            lastConversationMessage.put("EMPF", (resultLastMessage.getLong("FUID")));
            lastConversationMessage.put("PID", resultLastMessage.getString("PID"));
            lastConversationMessage.put("TYPE", resultLastMessage.getShort("TYPE"));
            lastConversationMessage.put("ST", (short) resultLastMessage.getShort("State"));
            lastConversationMessage.put("TIME", resultLastMessage.getTimestamp("TIME").getTime());
            lastConversationMessage.put("DES", resultLastMessage.getString("Beschreibung"));

            PreparedStatement prGetHashtagFromPost =
                    (PreparedStatement) super.requestHandler().getCurrentConnectionToSql().prepareStatement(SynchAllPostsFromAllUsers.queryGetHashtagsFromPost);
            prGetHashtagFromPost.setString(1, resultLastMessage.getString("PID"));

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
            lastConversationMessage.put("ARR_EHT", jsonArrayHashtags);

            PreparedStatement checkIfSaved = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                    .prepareStatement(SynchAllPostsFromAllUsers.queryLookUpIfPostSaved);
            checkIfSaved.setString(1, resultLastMessage.getString("PID"));
            ResultSet result = checkIfSaved.executeQuery();
            JSONArray jsonArray = new JSONArray();

            while(result.next() && (result.getLong("UID_POST_FROM") == super.requestHandler().getThreadUID() ||
                    result.getLong("UID_SAVED") == super.requestHandler().getThreadUID()))
            {
                jsonArray.put(result.getLong("UID_SAVED"));
            }
            result.close();
            checkIfSaved.close();
            lastConversationMessage.put("ARS", jsonArray);

            jsonArrayLastConversationMessages.put(lastConversationMessage);
        }

        resultLastMessage.close();
        prLastMessage.close();

        if (jsonArrayLastConversationMessages.length() > 0)
        {
            super.requestHandler().getWriter().println("1");
            super.requestHandler().getWriter().println(jsonArrayLastConversationMessages.toString());
        }
        else {
            super.requestHandler().getWriter().println("0");
        }
    }
}
