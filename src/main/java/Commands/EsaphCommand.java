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

public abstract class EsaphCommand
{
	private LogUtilsEsaph logUtilsRequest;
	private InformationServer.RequestHandler requestHandler;
	private InformationServer informationServer;
	
	public EsaphCommand(InformationServer plServer, InformationServer.RequestHandler requestHandler, LogUtilsEsaph logUtilsRequest)
	{
		this.informationServer = plServer;
		this.requestHandler = requestHandler;
		this.logUtilsRequest = logUtilsRequest;
	}
	
	public InformationServer.RequestHandler requestHandler()
	{
		return this.requestHandler;
	}
	
	public InformationServer plServer()
	{
		return this.informationServer;
	}
	
	public LogUtilsEsaph logUtilsRequest()
	{
		return this.logUtilsRequest;
	} 
	
	public abstract void run() throws Exception;
	
}
