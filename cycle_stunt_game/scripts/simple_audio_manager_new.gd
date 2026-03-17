extends Node

# Simple Audio Manager
class_name SimpleAudioManager

var sfx_player: AudioStreamPlayer2D

func _ready():
	sfx_player = AudioStreamPlayer2D.new()
	add_child(sfx_player)
	print("SimpleAudioManager: SFXPlayer created and added")

func play_sound(sound_path: String):
	var sound = load(sound_path)
	if sound and sfx_player:
		sfx_player.stream = sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Playing sound: ", sound_path)
	else:
		print("Failed to load sound: ", sound_path)

func play_pedal():
	play_sound("res://assets/sounds/pedal_loop.wav")

func play_jump():
	play_sound("res://assets/sounds/jump.wav")

func play_land():
	play_sound("res://assets/sounds/land.wav")

func play_crash():
	play_sound("res://assets/sounds/crash.wav")

func play_win():
	play_sound("res://assets/sounds/win.wav")

func play_click():
	play_sound("res://assets/sounds/click.wav")
