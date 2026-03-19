extends CanvasLayer

@onready var _time_label: Label = $Margin/Root/TopRow/Time
@onready var _level_label: Label = $Margin/Root/TopRow/Level
@onready var _score_label: Label = $Margin/Root/TopRow/Score
@onready var _message_label: Label = $Margin/Root/Message
@onready var _input_label: Label = $Margin/Root/InputDebug
@onready var _btn_accel: Button = $Margin/Root/Controls/Accel
@onready var _btn_stunt: Button = $Margin/Root/Controls/Stunt
@onready var _btn_brake: Button = $Margin/Root/Controls/Brake
@onready var _btn_back_to_menu: Button = $Margin/Root/Navigation/BackToMenu
@onready var _btn_restart: Button = $Margin/Root/Navigation/Restart

var _time := 0.0
var _running := true

var _score := 0


# Track action states manually since Input.action_press isn't working
var _action_states := {
	"accelerate": false,
	"brake": false,
	"stunt": false,
}

# Make action states globally accessible
static var _global_action_states = {
	"accelerate": false,
	"brake": false,
	"stunt": false,
}

func _ready() -> void:
	# Hide debug input label
	_input_label.visible = false
	# Make mobile buttons taller
	_btn_accel.size = Vector2(0, 120)
	_btn_brake.size = Vector2(0, 120)
	_btn_stunt.size = Vector2(0, 120)
	_btn_restart.size = Vector2(0, 90)
	_btn_back_to_menu.size = Vector2(0, 90)
	
	# Increase button font size for mobile
	_btn_accel.add_theme_font_size_override("font_size", 40)
	_btn_brake.add_theme_font_size_override("font_size", 40)
	_btn_stunt.add_theme_font_size_override("font_size", 40)
	_btn_restart.add_theme_font_size_override("font_size", 32)
	_btn_back_to_menu.add_theme_font_size_override("font_size", 32)
	
	# Connect button signals with sound effects
	_btn_accel.button_down.connect(func(): 
		_press_action("accelerate")
	)
	_btn_accel.button_up.connect(func(): _release_action("accelerate"))
	_btn_stunt.button_down.connect(func(): 
		_press_action("stunt")
	)
	_btn_stunt.button_up.connect(func(): _release_action("stunt"))
	_btn_brake.button_down.connect(func(): 
		_press_action("brake")
	)
	_btn_brake.button_up.connect(func(): _release_action("brake"))
	# Connect navigation buttons (guard against duplicate connections)
	if not _btn_restart.pressed.is_connected(_on_restart_pressed):
		_btn_restart.pressed.connect(func():
			# Don't play click sound for restart to avoid crash sound conflict
			_on_restart_pressed()
		)
	set_score(0)

func _process(delta):
	if _running:
		_time += delta
		_time_label.text = "Time: %.2f" % _time
	

func reset_timer() -> void:
	_time = 0.0
	_running = true

func set_level(level_number: int) -> void:
	_level_label.text = "Level: %d" % level_number

func get_message() -> String:
	return _message_label.text

func set_score(value: int) -> void:
	_score = value
	if _score_label != null:
		_score_label.text = "Score: %d" % _score

func set_message(text: String) -> void:
	_message_label.text = text

func _press_action(action_name: String) -> void:
	Input.action_press(action_name)
	_action_states[action_name] = true
	_global_action_states[action_name] = true  # Update global state
	
func _release_action(action_name: String) -> void:
	Input.action_release(action_name)
	_action_states[action_name] = false
	_global_action_states[action_name] = false  # Update global state
	

func get_global_action_state(action_name: String) -> bool:
	return _global_action_states.get(action_name, false)

func _play_button_click() -> void:
	var main_scene = get_tree().current_scene
	if main_scene:
		var audio_manager = main_scene.get_node_or_null("SimpleAudioManager")
		if audio_manager and audio_manager.has_method("play_click"):
			audio_manager.call("play_click")
		else:
			print("SimpleAudioManager not found")

func _on_restart_pressed() -> void:
	var scene := get_tree().current_scene
	if scene != null and scene.has_method("restart_level"):
		# Optional: show interstitial on restart
		AdManager.load_interstitial()
		AdManager.ad_loaded.connect(func():
			if AdManager._interstitial_ad:
				AdManager.show_interstitial()
		, CONNECT_ONE_SHOT)
		scene.call("restart_level")

func _on_back_to_menu_pressed() -> void:
	get_tree().change_scene_to_file("res://scenes/main_menu.tscn")
