/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

import Esaph.LogUtilsEsaph;
import FServerMain.InformationServer;

import java.io.IOException;

public class FServer
{
	public static void main(String[] args) throws IOException
	{
		if(args.length > 0)
		{
			LogUtilsEsaph.logInConsole = Boolean.parseBoolean(args[0]);
		}

		InformationServer fr = new InformationServer();
		fr.startFServer();
	}
}
