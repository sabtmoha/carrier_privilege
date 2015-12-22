package com.orange;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

public class CarrierPrivilegeApplet extends Applet {
	
	/* Universal Constants */
	private static final short R_APDU_MAX_LENGTH = 256;
	
	/* Tags Constants */
    private static final byte  INS_GET_DATA       = (byte) 0xCA;
    private static final short P1P2_GET_DATA_ALL  = (short) 0xFF40;
    private static final short P1P2_GET_DATA_NEXT = (short) 0xFF60;
	
	private static final short SHA256_SIGN_OFF = 9;
	private static final short SHA256_SIGN_LEN = 32;
	
    
	//TODO Fill in the Gap
	/* SHA256 SIGN */
	private static final byte[] SHA256_SIGN = {
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
	};
	
	
    
    private final byte[] rules;
    private short rulesOffset;
    private short rulesLength;
    private boolean isEnabledGetDataNext;
	
	/**
	 * constructor: called only while installation 
	 */
	protected CarrierPrivilegeApplet() {		
		/* rules */
		rules = new byte[] {
			(byte) 0xFF, (byte) 0x40, (byte) 0x2B, (byte) 0xE2, (byte) 0x29, (byte) 0xE1, (byte) 0x22,
			(byte) 0xC1, (byte) 0x20, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xE3,
			(byte) 0x03, (byte) 0xDB, (byte) 0x01, (byte) 0x01
		};
		
		rulesOffset = 0;
		rulesLength = (short) rules.length;
		
		
		/* Put the signature */
		Util.arrayCopyNonAtomic(SHA256_SIGN, (short) 0, rules, SHA256_SIGN_OFF, SHA256_SIGN_LEN);		
		
		/* cannot be the first command to be called */
		isEnabledGetDataNext = false;
		
		/* compulsory */
		register();
	}
	
	/**
	 * @override
	 */
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		new CarrierPrivilegeApplet();
	}
	
	
	/**
	 * @override
	 */
	public void process(APDU apdu) throws ISOException {
		/* return success if select command */
		if(selectingApplet()) {
			ISOException.throwIt(ISO7816.SW_NO_ERROR);
		}
		
		final byte[] apduBuf = apdu.getBuffer();
		switch(apduBuf[ISO7816.OFFSET_INS]) {
			case INS_GET_DATA:
				final short p1p2 = Util.makeShort(apduBuf[ISO7816.OFFSET_P1], apduBuf[ISO7816.OFFSET_P2]);
				switch(p1p2) {
					case P1P2_GET_DATA_ALL:
						rulesOffset = 0;
						isEnabledGetDataNext = true;
						break;
					case P1P2_GET_DATA_NEXT:
						if(!isEnabledGetDataNext) {
							ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
						}
						/* no further next */
						isEnabledGetDataNext = ((short) (rulesLength - rulesOffset)) > R_APDU_MAX_LENGTH;
						break;
					default: ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
				}
				rulesOffset += getDataAll(apdu, rules, rulesOffset, rulesLength);
				break;
			default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}
	
	
	/*
	 * GET DATA All/Next
	 * @return the length of the sent bytes
	 */
	private short getDataAll(APDU apdu, byte[] data, short offset, short length) {
		/* compute length of data to be sent */
		short sendLength = (short) (length - offset);
		if(sendLength > R_APDU_MAX_LENGTH) {
			sendLength = R_APDU_MAX_LENGTH;
		}
		
		/* copy data to send */
		Util.arrayCopyNonAtomic(data, offset, apdu.getBuffer(), (short) 0, sendLength);
		
		/* send the apdu buffer as the R-APDU data */
		apdu.setOutgoingAndSend((short) 0, sendLength);
		
		return sendLength;
	}

}
