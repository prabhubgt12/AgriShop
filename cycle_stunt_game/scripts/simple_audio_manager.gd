extends Node

# Simple Audio Manager
var sfx_player: AudioStreamPlayer2D

func _ready():
	sfx_player = AudioStreamPlayer2D.new()
	add_child(sfx_player)
	print("SimpleAudioManager: SFXPlayer created and added")

func play_sound(sound_path: String):
	print("=== SOUND REQUEST ===")
	print("Requested sound: ", sound_path)
	var sound = load(sound_path)
	if sound and sfx_player:
		print("Sound loaded successfully, playing: ", sound_path)
		sfx_player.stream = sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
	else:
		print("ERROR: Failed to load or play sound: ", sound_path)
		if not sound:
			print("  Reason: Sound file not found or corrupted")
		if not sfx_player:
			print("  Reason: SFXPlayer not available")

func play_pedal():
	print("PEDAL LOOP REQUESTED")
	var sound = load("res://assets/sounds/pedal_loop.wav")
	if sound and sfx_player:
		print("Pedal sound loaded successfully, setting up manual looping")
		sfx_player.stream = sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		
		# Disconnect any existing finished signal to avoid duplicates
		if sfx_player.is_connected("finished", _on_pedal_finished):
			sfx_player.disconnect("finished", _on_pedal_finished)
		
		# Connect finished signal for manual looping
		sfx_player.connect("finished", _on_pedal_finished)
	else:
		print("ERROR: Failed to load pedal sound")
		if not sound:
			print("  Reason: Pedal sound file not found or corrupted")
		if not sfx_player:
			print("  Reason: SFXPlayer not available")

func _on_pedal_finished():
	print("Pedal sound finished, restarting for continuous loop")
	if sfx_player and sfx_player.stream:
		sfx_player.play()  # Restart the sound for looping

func play_jump():
	print("JUMP SOUND REQUESTED")
	play_sound("res://assets/sounds/jump.wav")

func play_land():
	print("LAND SOUND REQUESTED")
	play_sound("res://assets/sounds/land.wav")

func play_crash():
	print("CRASH SOUND REQUESTED")
	play_sound("res://assets/sounds/crash.wav")

func play_win():
	print("WIN SOUND REQUESTED")
	play_sound("res://assets/sounds/win.wav")

func play_click():
	print("CLICK SOUND REQUESTED")
	play_sound("res://assets/sounds/click.wav")

func stop_pedal():
	print("STOPPING PEDAL SOUND")
	if sfx_player and sfx_player.playing:
		sfx_player.stop()
