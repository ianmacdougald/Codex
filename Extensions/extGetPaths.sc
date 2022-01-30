+ String {
	getPaths { ^PathName(this).getPaths }

	getScripts {
		^this.getPaths.select({ | item |
			item[(item.size - 4)..]==".scd";
		});
	}
}

+ PathName {
	getPaths {
		if(this.isFile, {
			^[fullPath];
		});
		^this.entries.getPaths;
	}
}

+ Collection {
	getPaths {
		var strings = [];
		this.do { | item, index |
			strings = strings++item.getPaths;
		};
		^strings;
	}
}

+ Object {
	getPaths { ^nil }
}
