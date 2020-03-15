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
import org.json.JSONObject;

import java.sql.PreparedStatement;

public class UpdateProfilDescriptionPlopp extends EsaphCommand
{
    private static final String QUERY_UPDATE_DESCRIPTION = "UPDATE Users SET Description=? WHERE UID=? LIMIT 1";
    public UpdateProfilDescriptionPlopp(InformationServer plServer, InformationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
    {
        super(plServer, requestHandler, logUtilsRequest);
    }

    @Override
    public void run() throws Exception
    {
        JSONObject jsonObjectDescriptionPlopp = super.requestHandler().getJSONMessage().getJSONObject("DESCPL");

        PreparedStatement preparedStatement = null;
        try
        {
            preparedStatement = super.requestHandler().getCurrentConnectionToSql().prepareStatement(UpdateProfilDescriptionPlopp.QUERY_UPDATE_DESCRIPTION);
            preparedStatement.setString(1, jsonObjectDescriptionPlopp.toString());
            int result = preparedStatement.executeUpdate();

            if(result > 1)
            {
                super.requestHandler().getWriter().println("1");
            }
            else
            {
                super.requestHandler().getWriter().println("0");
            }
        }
        catch (Exception ec)
        {
            super.logUtilsRequest().writeLog("UpdateProfilDescriptionPlopp() failed: " + ec);
        }
        finally
        {
            if(preparedStatement != null)
            {
                preparedStatement.close();
            }
        }
    }
}
