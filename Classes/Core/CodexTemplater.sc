CodexTemplater {
	classvar defaultPath;
	var <>folder;

	*initClass { 
		defaultPath = Main.packages.asDict
		.at(\CodexIan)+/+"Classes/Templates";
	}

	*new { | folder |
		folder ?? { Error("No folder set.").throw };
		^super.newCopyArgs(folder.asString);
	}

	synthDef { | templateName("synthDef") |
		this.makeTemplate(templateName, defaultPath+/+"synthDef.scd");
	}

	pattern { | templateName("pattern") |
		this.makeTemplate(templateName, defaultPath+/+"pattern.scd");
	}

	function { | templateName("function") |
		this.makeTemplate(templateName, defaultPath+/+"function.scd");
	}

	synth { | templateName("synth") |
		this.makeTemplate(templateName, defaultPath+/+"node.scd");
	}

	event { | templateName("event") |
		this.makeTemplate(templateName, defaultPath+/+"event.scd");
	}

	array { | templateName("array") |
		this.makeTemplate(templateName, defaultPath+/+"array.scd");
	}

	list { | templateName("list") |
		this.makeTemplate(templateName, defaultPath+/+"list.scd");
	}

	blank { | templateName("module") |
		this.makeTemplate(templateName, defaultPath+/+"module.scd");
	}

	makeTemplate { | templateName, sourcePath |
		this.class.copyFile(templateName, sourcePath, folder);
	}

	*copyFile { | templateName, sourcePath, folder |
		var to = folder+/+templateName.asString++".scd";
		try({ File.copy(sourcePath, to) }, { File.copy(sourcePath, to.increment) });
	}
}
