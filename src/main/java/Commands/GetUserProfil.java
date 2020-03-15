/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package Commands;

import Esaph.LogUtilsEsaph;
import Esaph.ServerPolicy;
import FServerMain.InformationServer;
import com.mysql.jdbc.PreparedStatement;
import org.json.JSONObject;

import java.sql.ResultSet;

public class GetUserProfil extends EsaphCommand
{
    private static final String queryGetFullProfil = "SELECT * FROM Users WHERE UID=? LIMIT 1";
    private static final String queryGetCountPublicSaved = "SELECT ( SELECT COUNT(*) FROM Following WHERE FUID_FOLLOWING=?) AS COUNT_FOLLOWER, (SELECT COUNT(*) FROM Following WHERE UID_FOLLOWS=?) AS COUNT_FOLLOWS FROM dual";


    public GetUserProfil(InformationServer plServer, InformationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest) {
        super(plServer, requestHandler, logUtilsRequest);
    }


    @Override
    public void run() throws Exception
    {
        PreparedStatement preparedStatementGetProfil = null;
        PreparedStatement preparedStatementStatistics = null;
        ResultSet resultSetStatistics = null;
        ResultSet result = null;

        try
        {
            JSONObject jsonObjectUserProfile = new JSONObject();

            preparedStatementGetProfil = (PreparedStatement)
                    super.requestHandler().getCurrentConnectionToSql()
                    .prepareStatement(GetUserProfil.queryGetFullProfil);

            preparedStatementGetProfil.setLong(1, super.requestHandler().getJSONMessage().getLong("FUSRN"));
            result = preparedStatementGetProfil.executeQuery();
            if (result.next())
            {
                if (ServerPolicy.isAllowed(
                        super.requestHandler().getCurrentConnectionToSql(),
                        super.requestHandler().getThreadUID(),
                        result.getLong("UID"))) // Beide folgen einander, du kannst dir das Profil angucken.
                {
                    preparedStatementStatistics = (PreparedStatement) super.requestHandler().getCurrentConnectionToSql()
                            .prepareStatement(GetUserProfil.queryGetCountPublicSaved);
                    preparedStatementStatistics.setLong(1, result.getLong("UID"));
                    preparedStatementStatistics.setLong(2, result.getLong("UID"));
                    resultSetStatistics = preparedStatementStatistics.executeQuery();
                    if(resultSetStatistics.next())
                    {
                        jsonObjectUserProfile.put("FOW", resultSetStatistics.getInt("COUNT_FOLLOWER")); //Hat so viele follower
                        jsonObjectUserProfile.put("FOL", resultSetStatistics.getInt("COUNT_FOLLOWS")); //Folgt so vielen menschen
                    }
                    else
                    {
                        jsonObjectUserProfile.put("FOW", 0); //Hat so viele follower
                        jsonObjectUserProfile.put("FOL", 0); //Folgt so vielen menschen
                    }

                    jsonObjectUserProfile.put("USRN", result.getString("Benutzername"));
                    jsonObjectUserProfile.put("Vorname", result.getString("Vorname"));
                    jsonObjectUserProfile.put("PDesc", result.getString("Description"));
                    jsonObjectUserProfile.put("PBPolice", result.getShort("ProfilPublicity"));
                    jsonObjectUserProfile.put("Geburtstag", result.getTimestamp("Geburtstag").getTime());
                    jsonObjectUserProfile.put("Region", result.getString("Region"));
                }
            }

            super.requestHandler().getWriter().println(jsonObjectUserProfile.toString());
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("GetUserProfil run() failed: " + ec);
        }
        finally
        {
            if(preparedStatementGetProfil != null)
            {
                preparedStatementGetProfil.close();
            }

            if(preparedStatementStatistics != null)
            {
                preparedStatementStatistics.close();
            }

            if(resultSetStatistics != null)
            {
                resultSetStatistics.close();
            }

            if(result != null)
            {
                result.close();
            }
        }
    }
}
