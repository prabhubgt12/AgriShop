extends Node

# Audio Manager for Cycle Stunt Game
class_name AudioManager

var sfx_player: AudioStreamPlayer2D
var music_player: AudioStreamPlayer

# Sound effects
var pedal_loop_sound: AudioStream
var jump_sound: AudioStream
var land_sound: AudioStream
var crash_sound: AudioStream
var win_sound: AudioStream
var button_click_sound: AudioStream

func _ready():
	# Get audio players
	sfx_player = $SFXPlayer
	music_player = $MusicPlayer
	
	# Load sound files from res://sounds/
	pedal_loop_sound = load("res://sounds/pedal_loop.wav")
	jump_sound = load("res://sounds/jump.wav")
	land_sound = load("res://sounds/land.wav")
	crash_sound = load("res://sounds/crash.wav")
	win_sound = load("res://sounds/win.wav")
	button_click_sound = load("res://sounds/click.wav")
	
	# Debug: Check if sounds loaded correctly
	print("=== AUDIO DEBUG ===")
	print("SFXPlayer found: ", sfx_player != null)
	print("MusicPlayer found: ", music_player != null)
	print("pedal_loop_sound loaded: ", pedal_loop_sound != null)
	print("jump_sound loaded: ", jump_sound != null)
	print("land_sound loaded: ", land_sound != null)
	print("crash_sound loaded: ", crash_sound != null)
	print("win_sound loaded: ", win_sound != null)
	print("button_click_sound loaded: ", button_click_sound != null)
	print("=== AUDIO DEBUG END ===")

func play_pedal_loop():
	print("Attempting to play pedal loop")
	if pedal_loop_sound and sfx_player:
		sfx_player.stream = pedal_loop_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)  # Variation
		sfx_player.play()
		print("Pedal loop playing")
	else:
		print("ERROR: pedal_loop_sound or sfx_player is null")

func stop_pedal_loop():
	print("Stopping pedal loop")
	if sfx_player.playing:
		sfx_player.stop()

func play_jump_sound():
	print("Attempting to play jump sound")
	if jump_sound and sfx_player:
		sfx_player.stream = jump_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Jump sound playing")
	else:
		print("ERROR: jump_sound or sfx_player is null")

func play_land_sound():
	print("Attempting to play land sound")
	if land_sound and sfx_player:
		sfx_player.stream = land_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Land sound playing")
	else:
		print("ERROR: land_sound or sfx_player is null")

func play_crash_sound():
	print("Attempting to play crash sound")
	if crash_sound and sfx_player:
		sfx_player.stream = crash_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Crash sound playing")
	else:
		print("ERROR: crash_sound or sfx_player is null")

func play_win_sound():
	print("Attempting to play win sound")
	if win_sound and sfx_player:
		sfx_player.stream = win_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Win sound playing")
	else:
		print("ERROR: win_sound or sfx_player is null")

func play_button_click():
	print("Attempting to play button click")
	if button_click_sound and sfx_player:
		sfx_player.stream = button_click_sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()
		print("Button click playing")
	else:
		print("ERROR: button_click_sound or sfx_player is null")

func play_background_music(music_file: String):
	var music = load(music_file)
	if music and music_player:
		music_player.stream = music
		music_player.play()

func set_sfx_volume(value: float):
	if sfx_player:
		sfx_player.volume_db = linear_to_db(value)

func set_music_volume(value: float):
	if music_player:
		music_player.volume_db = linear_to_db(value)
