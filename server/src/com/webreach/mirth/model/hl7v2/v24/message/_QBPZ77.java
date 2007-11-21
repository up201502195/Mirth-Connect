package com.webreach.mirth.model.hl7v2.v24.message;
import com.webreach.mirth.model.hl7v2.v24.segment.*;
import com.webreach.mirth.model.hl7v2.*;

public class _QBPZ77 extends Message{	
	public _QBPZ77(){
		segments = new Class[]{_MSH.class, _QPD.class, _RDF.class, _RCP.class, _DSC.class};
		repeats = new int[]{0, 0, 0, 0, 0};
		required = new boolean[]{true, true, false, true, false};
		groups = new int[][]{}; 
		description = "Tabular Patient List";
		name = "QBPZ77";
	}
}
