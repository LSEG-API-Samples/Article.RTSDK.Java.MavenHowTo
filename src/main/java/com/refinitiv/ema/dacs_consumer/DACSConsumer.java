///*|----------------------------------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      	--
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  					--
// *|           Copyright (C) 2021 Refinitiv. All rights reserved.            		--
///*|----------------------------------------------------------------------------------------------------



package com.refinitiv.ema.dacs_consumer;

import com.refinitiv.ema.access.*;
import com.refinitiv.ema.access.DataType.DataTypes;
import com.refinitiv.ema.rdm.EmaRdm;

import java.nio.ByteBuffer;


import com.reuters.rfa.dacs.*;
import java.util.Arrays;

class AppClient implements OmmConsumerClient
{

    // to get permissionData as String in order to print permissionData correctly.
    private StringBuilder getPermissionDataAsSB(StringBuilder temp, ByteBuffer buffer)
    {
        int limit = buffer.limit();
        for (int i = buffer.position(); i < limit; i++)
            temp.append(i + 1 == limit ? String.format("%02x", buffer.get(i))
                    : String.format("%02x ", buffer.get(i)));

        return temp;
    }

    public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event)
    {
        System.out.println("Item Name: " + (refreshMsg.hasName() ? refreshMsg.name() : "<not set>"));
        System.out.println("Service Name: " + (refreshMsg.hasServiceName() ? refreshMsg.serviceName() : "<not set>"));

        System.out.println("Item State: " + refreshMsg.state());

        if (DataType.DataTypes.FIELD_LIST == refreshMsg.payload().dataType())
            decode(refreshMsg.payload().fieldList());

        //OpenDACS

        byte[] arr = new byte[refreshMsg.permissionData().remaining()];
        refreshMsg.permissionData().get(arr);
        AuthorizationLock authLock;
        try {
            authLock = new AuthorizationLock(arr);
            AuthorizationLockResult lockResult = AuthorizationLockUtility.getStructuredServiceIDLock(authLock);
            if (lockResult.getStatusCode() == AuthorizationLockResult.SUCCESS) {
                if (lockResult.getType() == AuthorizationLockResult.AS_AuthorizationStructuredServiceIDLock) {
                    AuthorizationStructuredServiceIDLock[] serviceIdPEsArray = (AuthorizationStructuredServiceIDLock[])lockResult.getData();
                    if (serviceIdPEsArray.length != 1) {
                        System.out.println("This command does not support multiple service DACSLOCK instance");
                        return;
                    }
                    AuthorizationStructuredServiceIDLock serviceIdPEs = serviceIdPEsArray[0];
                    StringBuilder permissionDataSB = new StringBuilder("");
                    System.out.println(Arrays.toString(refreshMsg.permissionData().array()) + "\n");
                    System.out.println("PermissionData: " + getPermissionDataAsSB(permissionDataSB, refreshMsg.permissionData()).toString());
                    System.out.println("PermissionData: ServiceId is " + serviceIdPEs.getServiceID());
                    System.out.println("PermissionData: The list of PEs are ");
                    long[] PEs = serviceIdPEs.getPEList();
                    StringBuffer PEsSB = new StringBuffer("");
                    for(long aPE:PEs) {
                        PEsSB.append(aPE).append(",");
                    }
                    PEsSB.deleteCharAt(PEsSB.length()-1);
                    System.out.println(PEsSB.toString());
                }
            }
        } catch (Exception e) {
// TODO Auto-generated catch block
            e.printStackTrace();
        }

        System.out.println();
    }

    public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event)
    {
        System.out.println("Item Name: " + (updateMsg.hasName() ? updateMsg.name() : "<not set>"));
        System.out.println("Service Name: " + (updateMsg.hasServiceName() ? updateMsg.serviceName() : "<not set>"));

        if (DataType.DataTypes.FIELD_LIST == updateMsg.payload().dataType())
            decode(updateMsg.payload().fieldList());

        System.out.println();
    }

    public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event)
    {
        System.out.println("Item Name: " + (statusMsg.hasName() ? statusMsg.name() : "<not set>"));
        System.out.println("Service Name: " + (statusMsg.hasServiceName() ? statusMsg.serviceName() : "<not set>"));

        if (statusMsg.hasState())
            System.out.println("Item State: " +statusMsg.state());

        System.out.println();
    }

    public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent){}
    public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent){}
    public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent){}

    void decode(FieldList fieldList)
    {
        for (FieldEntry fieldEntry : fieldList)
        {
            System.out.print("Fid: " + fieldEntry.fieldId() + " Name = " + fieldEntry.name() + " DataType: " + DataType.asString(fieldEntry.load().dataType()) + " Value: ");

            if (Data.DataCode.BLANK == fieldEntry.code())
                System.out.println(" blank");
            else
                switch (fieldEntry.loadType())
                {
                    case DataTypes.REAL :
                        System.out.println(fieldEntry.real().asDouble());
                        break;
                    case DataTypes.DATE :
                        System.out.println(fieldEntry.date().day() + " / " + fieldEntry.date().month() + " / " + fieldEntry.date().year());
                        break;
                    case DataTypes.TIME :
                        System.out.println(fieldEntry.time().hour() + ":" + fieldEntry.time().minute() + ":" + fieldEntry.time().second() + ":" + fieldEntry.time().millisecond());
                        break;
                    case DataTypes.DATETIME :
                        System.out.println(fieldEntry.dateTime().day() + " / " + fieldEntry.dateTime().month() + " / " +
                                fieldEntry.dateTime().year() + "." + fieldEntry.dateTime().hour() + ":" +
                                fieldEntry.dateTime().minute() + ":" + fieldEntry.dateTime().second() + ":" +
                                fieldEntry.dateTime().millisecond() + ":" + fieldEntry.dateTime().microsecond()+ ":" +
                                fieldEntry.dateTime().nanosecond());
                        break;
                    case DataTypes.INT :
                        System.out.println(fieldEntry.intValue());
                        break;
                    case DataTypes.UINT :
                        System.out.println(fieldEntry.uintValue());
                        break;
                    case DataTypes.ASCII :
                        System.out.println(fieldEntry.ascii());
                        break;
                    case DataTypes.ENUM :
                        System.out.println(fieldEntry.hasEnumDisplay() ? fieldEntry.enumDisplay() : fieldEntry.enumValue());
                        break;
                    case DataTypes.RMTES :
                        System.out.println(fieldEntry.rmtes());
                        break;
                    case DataTypes.ERROR :
                        System.out.println("(" + fieldEntry.error().errorCodeAsString() + ")");
                        break;
                    default :
                        System.out.println();
                        break;
                }
        }
    }
}

public class DACSConsumer
{
    public static void main(String[] args)
    {
        OmmConsumer consumer = null;
        try
        {
            AppClient appClient = new AppClient();

            consumer  = EmaFactory.createOmmConsumer(EmaFactory.createOmmConsumerConfig().host("localhost:14002").username("user"));

            ElementList view = EmaFactory.createElementList();
            OmmArray array = EmaFactory.createOmmArray();

            array.fixedWidth(2);
            array.add(EmaFactory.createOmmArrayEntry().intValue(1));


            view.add(EmaFactory.createElementEntry().uintValue(EmaRdm.ENAME_VIEW_TYPE, 1));
            view.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_VIEW_DATA, array));

            consumer.registerClient(EmaFactory.createReqMsg().serviceName("ELEKTRON_DD").name("IBM.N").payload(view), appClient);

            Thread.sleep(60000);			// API calls onRefreshMsg(), onUpdateMsg() and onStatusMsg()
        }
        catch (InterruptedException | OmmException excp)
        {
            System.out.println(excp.getMessage());
        }
        finally
        {
            if (consumer != null) consumer.uninitialize();
        }
    }
}