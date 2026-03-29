extends Node

# Simple Audio Manager
var sfx_player: AudioStreamPlayer2D
var pedal_player: AudioStreamPlayer
var _pedal_stream: AudioStream
var _pedal_timer: Timer
var _pedal_keep_playing := false

func _ready():
	sfx_player = AudioStreamPlayer2D.new()
	add_child(sfx_player)
	
	pedal_player = AudioStreamPlayer.new()
	add_child(pedal_player)
	
	# Copy bus/volume from sfx_player to pedal_player to ensure it's audible
	pedal_player.bus = sfx_player.bus
	pedal_player.volume_db = sfx_player.volume_db
	
	# Load pedal stream (loop enabled via import)
	_pedal_stream = load("res://assets/sounds/pedal_loop.wav")
	
	# Create timer for pedal retrigger
	_pedal_timer = Timer.new()
	_pedal_timer.one_shot = false
	_pedal_timer.wait_time = 0.2
	_pedal_timer.timeout.connect(_on_pedal_timer_timeout)
	add_child(_pedal_timer)

func _process(_delta):
	pass

func play_sound(sound_path: String):
	var sound = load(sound_path)
	if sound and sfx_player:
		sfx_player.stream = sound
		sfx_player.pitch_scale = randf_range(0.9, 1.1)
		sfx_player.play()

func play_pedal():
	_pedal_keep_playing = true
	if pedal_player and _pedal_stream and not pedal_player.playing:
		pedal_player.stream = _pedal_stream
		pedal_player.pitch_scale = 1.0
		pedal_player.play()
	if _pedal_timer:
		_pedal_timer.start()  # Start timer for continuous retrigger


func _on_pedal_timer_timeout():
	if not _pedal_keep_playing:
		return
	if not pedal_player:
		return
	if pedal_player.playing:
		return
	if not _pedal_stream:
		return
	pedal_player.stream = _pedal_stream
	pedal_player.pitch_scale = 1.0
	pedal_player.play()

func play_land():
	play_sound("res://assets/sounds/land.wav")

func play_crash():
	play_sound("res://assets/sounds/crash.wav")

func play_win():
	play_sound("res://assets/sounds/win.wav")

func play_click():
	play_sound("res://assets/sounds/click.wav")

func stop_pedal():
	_pedal_keep_playing = false
	if _pedal_timer:
		_pedal_timer.stop()
	if pedal_player and pedal_player.playing:
		pedal_player.stop()
