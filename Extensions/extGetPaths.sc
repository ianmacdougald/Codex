+ String{
	getPaths { ^PathName(this).getPaths }

	getAudioPaths {
		^this.getPaths.select({ | item |
			this.isValidAudioPath(item);
		});
	}

	getScriptPaths {
		^this.getPaths.select({ | item |
			this.isValidScript(item);
		});
	}

	isValidScript { | input | ^(PathName(input).extension=="scd") }

	isValidAudioPath { | input |
		var ext = PathName(input).extension;
		^this.class.validAudioPaths.find([ext]).notNil;
	}

	*validAudioPaths {
		^[
			"wav",
			"aiff",
			"oof",
			"mp3"
		];
	}

	getBuffers { ^this.getAudioPaths.collect(_.asBuffer) }
}

+ Buffer{
	getPaths { ^PathName(this.path).getPaths }
}

+ PathName{
	getPaths {
		var entries;
		if(this.isFile, {
			^[fullPath];
		});
		^this.entries.getPaths;
	}

	getAudioPaths { ^fullPath.getAudioPaths }
}

+ Array {
	getPaths {
		var strings = [];
		this.do{|item, index|
			strings = strings++item.getPaths;
		};
		^strings.as(this.class);
	}

	getAudioPaths {
		var strings = [];
		this.do{|item, index|
			strings = strings++item.getAudioPaths;
		};
		^strings.as(this.class);
	}
}

+ Object {
	getPaths{ ^nil }
}
