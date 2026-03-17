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
	play_sound("res://assets/sounds/pedal_loop.wav")

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
