CrummySequencer : HybridAbstraction {
	var window, routine, <tempo, <clock;
	var <>synthDef = \pmosc, synthEvent;
	var <buttons;

	*new{|rows = 4, tempo = 1|
		^super.new
		.prMakeSynthEvent
		.tempo_(tempo)
		.prMakeSequencer(rows);
	}

	setClock {
		clock = clock ? TempoClock(tempo ? 1.0);
	}

	tempo_{|newTempo|
		this.setClock;
		clock.tempo = newTempo;
	}

	closeAction {
		clock.stop;
		clock = nil;
		routine.stop;
		this.free;
	}

	close{
		window.close;
	}

	free{
		if(window.isClosed.not, {
			window.close;
		});
		super.free;
	}

	*defineSynthDefs{|synthDictionary|
		var synthdef;

		synthdef = SynthDef.new(\sine, {
			var env = EnvGen.ar(Env.perc(\atk.kr(0), \release.kr(1)), doneAction: 2);
			var sig = SinOsc.ar(\freq.kr(400));
			var out = sig * env * \amp.kr(0.5);
			Out.ar(\outchan.kr(0), Pan2.ar(out, \pan.kr(0)));
		});

		this.registerSynthDef(synthdef);

		synthdef = SynthDef.new(\pmosc, {
			var atk = \atk.kr(0), release = \release.kr(1);
			var env = EnvGen.ar(Env.perc(atk, release), doneAction: 2);
			var carFreqMod = EnvGen.ar(
				Env.perc(atk, release * \carModReleaseTime.kr(1.0)), doneAction: 0);
			var freq = \freq.kr(400);
			var sig = PMOsc.ar(freq * carFreqMod.range(1, \carModRatio.kr(3)),
				freq * \modFreqRatio.kr(0.5), \pmindex.kr(3));
			var out = sig * env * \amp.kr(0.5);
			Out.ar(\outchan.kr(0), Pan2.ar(out, \pan.kr(0)));
		}).add;

		this.registerSynthDef(synthdef);

		synthdef = SynthDef.new(\hh, {
			var freq = \freq.kr(400);
			var env = EnvGen.ar(Env.perc(\atk.kr(0), \release.kr(1)), doneAction: 2);
			var sig = HPF.ar(WhiteNoise.ar(), \hpfcutoff.kr(3000));
			var out = sig * env * \amp.kr(0.5);
			Out.ar(\outchan.kr(0), Pan2.ar(out, \pan.kr(0)));
		});

		this.registerSynthDef(synthdef);

		synthdef = SynthDef.new(\snare, {
			var freq = \freq.kr(400);
			var env = EnvGen.ar(Env.perc(\atk.kr(0), \release.kr(1)), doneAction: 2);
			var sig = LFNoise1.ar(3000);
			var out = sig * env * \amp.kr(0.5);
			Out.ar(\outchan.kr(0), Pan2.ar(out, \pan.kr(0)));
		});

		this.registerSynthDef(synthdef);

		synthdef = SynthDef.new(\varsaw, {
			var freq = \freq.kr(400);
			var atk = \atk.kr(0), release = \release.kr(1);
			var env = EnvGen.ar(Env.perc(atk, release), doneAction: Done.freeSelf);
			var filterEnv = EnvGen.ar(Env.perc(atk+\ffAtkOffset.kr(0),
				release * \ffReleaseRatio.kr(1)));
			var sig = VarSaw.ar(freq, \phase.kr(0.25),
				SinOsc.kr(\widthRate.kr(7) + \widthOffset.kr(#[0.0, 0.01]))
				.range(\widthLo.kr(0.25), \widthHi.kr(0.75))
			);
			var filteredSig = BMoog.ar(sig.sum,
				freq * filterEnv.range(\ffRatioLo.kr(1.25), \ffRatioHi.kr(4)), \q.kr(0.5));
			var out = filteredSig * env * \amp.kr(0.5);
			Out.ar(\outchan.kr(0), Pan2.ar(out, \pan.kr(0)));
		});

		this.registerSynthDef(synthdef);
	}

	prCloseAction{
		clock.stop;
		routine.stop;
	}

	prMakeSynthEvent {
		var makeName = {|input|
			this.class.formatSynthName(input);
		};
		synthEvent = (
			kick: { |ev, amp = 0.5|
				Synth(makeName.value(\pmosc), [
					\freq, 42.5,
					\carModRatio, 3,
					\carModReleaseTime,0.03,
					\release, 0.75,
					\pmindex, 1,
					\amp, amp
				])
			},

			hh: {|ev, amp = 0.35|
				Synth(makeName.value(\hh), [
					\release, 0.05,
					\pan, 1.0.bilinrand,
					\amp, amp
				])
			},

			snare: {|ev, amp = 0.35|
				Synth(makeName.value(\snare), [
					\release, 0.105,
					\pan, 0.5.bilinrand,
					\amp, amp
				])
			},

			sine: {|ev, amp = 0.5, freq = 400|
				Synth(makeName.value(\sine), [
					\release, 0.75,
					\filterReleaseTime, 0.5,
					\atk, 0.1,
					\amp, amp,
					\freq, freq
				])
			},

			varsaw: {|ev, amp = 0.125, freq = 400|
				Synth(makeName.value(\varsaw), [
					\widthRate, 3,
					\filterReleaseTime, 0.5,
					\freq, freq,
					\amp, amp,
					\atk, 0.1
				])
			}
		);
	}

	prMakeSingleButton {
		^Button(window)
		.states_([
			["", Color.black],
			["", Color.black, Color.yellow]
		]);
	}

	prMakeButtons {|size|
		if(buttons.isNil){
			buttons = Array.fill(synthEvent.size, {
				Array.fill(size, {
					this.prMakeSingleButton;
				});
			});
		};
	}

	prMakeSequencer { |size|
		var pointerButtons;

		window = Window("A Crummy Sequencer", Rect(100, 800, 100, 100))
		.front.alwaysOnTop_(true);

		this.prMakeButtons(size);

		pointerButtons = Array.fill(2, {
			Array.fill(size, {
				Button(window)
				.states_([
					["", Color.black, Color.black],
					["", Color.black, Color.red]
				])
				.acceptsMouse = false;
			});
		});

		routine = Routine({
			var counter = 0;
			var sineFreqs = Place([0, 2, 4, [7, 6, 6, 5, 5, 6, 6, 7]], inf).asStream;
			var varSawFreqs = Place([7, 4, 5, [10, 3, 3, 2, 1, -1, 3, 4]], inf).asStream;
			loop{
				var freqForVarSaw = Scale.major
				.degreeToFreq(varSawFreqs.next, 48.midicps, 1);
				var freqForSine = Scale.major.
				degreeToFreq(sineFreqs.next, 60.midicps, 1);
				{
					pointerButtons.do{|array|
						array.do{|item, index|
							if(index==counter){
								item.valueAction_(1);
							}{
								item.valueAction_(0);
							}
						}
					};
					buttons.do{|column, index|
						if(column[counter].value == 1){
							case
							{index==0}{synthEvent.kick(0.25)}
							{index==1}{synthEvent.hh(0.125)}
							{index==2}{synthEvent.snare(0.25)}
							{index==3}{synthEvent.sine(0.01, freqForSine)}
							{index==4}{synthEvent.varsaw(0.01, freqForVarSaw)}
						};
					}
				}.defer;
				counter = counter + 1 % buttons[0].size;
				(clock.beatsPerBar * (1/8)).wait;
			}
		}).play(clock);
		window.onClose = {
			this.closeAction;
		};
		window.layout = GridLayout.rows(
			pointerButtons[0],
			buttons[0],
			buttons[1],
			buttons[2],
			buttons[3],
			buttons[4],
			pointerButtons[1]
		);
		CmdPeriod.doOnce({this.close});
	}

}
