+ String {
	exists { ^this.pathMatch.isEmpty.not }

	copyScriptsTo { | newDirectory |
		this.getScripts.do { | path |
			File.copy(path, newDirectory+/+PathName(path).fileName);
		};
	}
	//I just copied these methods from PathName...
	noEndNumbers {
		^this[..this.endNumberIndex]
	}

	endNumber {	// turn consecutive digits at the end of fullPath into a number.
		^(try { this[this.endNumberIndex + 1..] }{ this }).asInteger;
	}

	endNumberIndex {
		var index = this.lastIndex;
		while({
			index > 0 and: { this.at(index).isDecDigit }
		}, {
			index = index - 1
		});
		^index
	}

	runInGnome { | shell = "sh" |
		if("which gnome-terminal".unixCmdGetStdOut!=""){
			("gnome-terminal -- "+shell+" -i -c "+this.shellQuote).unixCmd;
			^true;
		} { ^false };
	}

	compileFile {
		if(this.exists){
			^thisProcess.interpreter.compileFile(this);
		}{ ^nil };
	}
}
