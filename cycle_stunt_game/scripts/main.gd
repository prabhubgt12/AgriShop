extends Node2D

@export var levels: Array[PackedScene] = []

var _current_level: Node2D
var _is_level_complete := false
var _is_game_over := false  # Track game over state
var _level_to_load := -1  # Store which level to load after scene change
var _current_level_index := -1  # Track current level index

# Audio manager directly in main
var sfx_player: AudioStreamPlayer2D

# Static variable to pass level index between scenes
static var pending_level_index := -1

func _ready() -> void:
	# Create audio player directly
	sfx_player = AudioStreamPlayer2D.new()
	add_child(sfx_player)
	print("Main: SFXPlayer created and added")
	
	# Add simple audio manager first
	var audio_manager = load("res://scripts/simple_audio_manager.gd").new()
	audio_manager.name = "SimpleAudioManager"
	add_child(audio_manager)
	print("Simple audio manager created and added to main scene")
	
	if levels.is_empty():
		levels = [
			preload("res://scenes/level_1.tscn"),
			preload("res://scenes/level_2.tscn"),
			preload("res://scenes/level_3.tscn"),
			preload("res://scenes/level_4.tscn"),
			preload("res://scenes/level_5.tscn"),
		]
	
	# Check if we need to load a specific level from menu
	if pending_level_index >= 0:
		_load_level(pending_level_index)
		pending_level_index = -1  # Reset after loading
	else:
		# Start with main menu if no level is pending
		get_tree().change_scene_to_file("res://scenes/main_menu.tscn")


func load_level_from_menu(level_index: int) -> void:
	pending_level_index = level_index
	get_tree().change_scene_to_file("res://scenes/main.tscn")



func _load_level(index: int) -> void:
	print("=== LOADING LEVEL DEBUG START ===")
	print("Requested level index: ", index)
	print("Total available levels: ", levels.size())
	
	_current_level_index = clamp(index, 0, levels.size() - 1)
	print("Clamped level index: ", _current_level_index)
	
	if _current_level != null:
		print("Clearing existing level...")
		_current_level.queue_free()
	
	print("Instantiating level ", _current_level_index)
	_current_level = levels[_current_level_index].instantiate()
	print("Level instantiated successfully")
	
	add_child(_current_level)
	print("Level added to scene tree")
	
	print("=== LEVEL LOADING DEBUG ===")
	print("Connecting signals to level...")
	
	# Check if level has the required nodes
	if not _current_level.has_node("Bike/Frame"):
		print("ERROR: Level missing Bike/Frame node!")
		return
	
	if not _current_level.has_node("Bike"):
		print("ERROR: Level missing Bike node!")
		return
	
	_current_level.connect("game_over", _on_game_over)
	print("Connected game_over signal")
	_current_level.connect("level_complete", _on_level_complete)
	print("Connected level_complete signal")

	# Update HUD with current level
	var hud := get_node_or_null("HUD")
	if hud == null:
		print("Creating new HUD...")
		var hud_scene = preload("res://scenes/hud.tscn")
		hud = hud_scene.instantiate()
		hud.name = "HUD"
		add_child(hud)
		hud.call("set_level", _current_level_index + 1)
		hud.call("reset_timer")
		hud.call("set_message", "")
	else:
		print("Using existing HUD...")
		hud.call("set_level", _current_level_index + 1)
		hud.call("reset_timer")
		hud.call("set_message", "")
	
	print("=== LEVEL LOADING COMPLETE ===")

func restart_level() -> void:
	print("Actually restarting level now...")
	_is_game_over = false  # Reset game over state
	
	# Clear any existing popups
	for child in get_tree().current_scene.get_children():
		if child is AcceptDialog:
			child.queue_free()
	
	_load_level(_current_level_index)

func _on_game_over() -> void:
	print("=== GAME OVER CALLED ===")
	if _is_game_over:  # Prevent multiple calls
		print("Already game over, returning")
		return
		
	_is_game_over = true
	print("Setting game_over flag to true")
	
	# Play crash sound
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager and audio_manager.has_method("play_crash"):
		audio_manager.call("play_crash")
	else:
		print("SimpleAudioManager not found for crash sound")
	
	var hud := get_node_or_null("HUD")
	if hud != null:
		hud.call("set_message", "Crashed!")
	else:
		print("HUD not found!")
	
	# Stop the bike
	if _current_level != null:
		var bike = _current_level.get_node_or_null("Bike/Frame")
		if bike != null and bike.has_method("stop_bike"):
			bike.call("stop_bike")
			print("Stopped bike")
		else:
			print("Bike frame not found or no stop_bike method")
	else:
		print("Current level is null!")
	
	# Show popup dialog with choices
	_show_crash_popup()

func _show_crash_popup() -> void:
	print("=== SHOW CRASH POPUP START ===")
	var dialog := AcceptDialog.new()
	dialog.title = "Crashed!"
	dialog.dialog_text = "Choose an option:"
	
	# Make popup mobile-friendly
	dialog.size = Vector2(400, 200)
	dialog.add_theme_font_size_override("font_size", 24)
	
	# Hide default OK button
	dialog.get_ok_button().hide()
	
	# Add custom buttons with larger size
	var restart_btn := dialog.add_button("Restart Immediately", false, "restart")
	var ad_btn := dialog.add_button("Watch Ad for Bonus", false, "watch_ad")
	
	# Make buttons larger
	restart_btn.size = Vector2(180, 60)
	ad_btn.size = Vector2(180, 60)
	restart_btn.add_theme_font_size_override("font_size", 20)
	ad_btn.add_theme_font_size_override("font_size", 20)
	
	print("Connecting popup signals...")
	dialog.connect("custom_action", _on_crash_popup_choice)
	
	# Add dialog to current scene
	var current_scene = get_tree().current_scene
	print("Current scene: ", current_scene.name if current_scene else "NULL")
	current_scene.add_child(dialog)
	print("Dialog added as child")
	
	dialog.popup_centered()
	print("Popup centered and shown!")
	print("=== SHOW CRASH POPUP END ===")

func _on_crash_popup_choice(action: String) -> void:
	print("Popup choice received: ", action)
	
	# Close popup first
	var popup = get_tree().current_scene.get_child(-1)  # Get last added child (the popup)
	if popup is AcceptDialog:
		popup.queue_free()
	
	if action == "restart":
		print("Restarting level...")
		restart_level()
	elif action == "watch_ad":
		print("Watch Ad chosen...")
		_show_rewarded_ad_and_restart()

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

func _show_rewarded_ad_and_restart() -> void:
	print("Starting to load rewarded ad...")
	
	var rewarded_loaded := false
	
	# Connect to signals
	var on_ad_loaded = func(ad_type: String):
		if ad_type == "rewarded":
			rewarded_loaded = true
			print("Rewarded ad loaded successfully!")
	
	var on_ad_failed = func(ad_type: String, error: String):
		if ad_type == "rewarded":
			print("Rewarded ad failed to load: ", error)
	
	AdManager.ad_loaded.connect(on_ad_loaded)
	AdManager.ad_failed_to_load.connect(on_ad_failed)
	
	# Load rewarded ad
	AdManager.load_rewarded()
	print("Waiting for rewarded ad to load...")
	
	# Wait for ad to load or timeout
	var timeout := 5.0
	var start_time := Time.get_time_dict_from_system()
	
	while not rewarded_loaded and (Time.get_time_dict_from_system()["hour"] * 3600 + Time.get_time_dict_from_system()["minute"] * 60 + Time.get_time_dict_from_system()["second"] - start_time["hour"] * 3600 - start_time["minute"] * 60 - start_time["second"]) < timeout:
		await get_tree().create_timer(0.1).timeout
	
	# Disconnect signals
	AdManager.ad_loaded.disconnect(on_ad_loaded)
	AdManager.ad_failed_to_load.disconnect(on_ad_failed)
	
	if rewarded_loaded and AdManager._rewarded_ad:
		print("Showing rewarded ad...")
		AdManager.show_rewarded()
		print("Waiting for ad to close...")
		await AdManager.ad_closed
		print("Ad closed, restarting with bonus!")
		var hud := get_node_or_null("HUD")
		if hud != null:
			hud.call("set_message", "Bonus! Restarting...")
		await get_tree().create_timer(1.0).timeout
		restart_level()
	else:
		print("Rewarded ad failed to load or timeout, restarting anyway...")
		restart_level()

func _on_ad_failed(ad_type: String, error: String) -> void:
	print("Ad failed to load: ", ad_type, " Error: ", error)

func _on_level_complete() -> void:
	var hud := get_node_or_null("HUD")
	if hud != null:
		hud.call("set_message", "Level Complete!")
	
	# Stop the bike from moving
	if _current_level != null:
		var bike = _current_level.get_node_or_null("Bike/Frame")
		if bike != null and bike.has_method("stop_bike"):
			bike.call("stop_bike")

	# Load and show interstitial ad
	AdManager.load_interstitial()
	await AdManager.ad_loaded
	if AdManager._interstitial_ad:
		AdManager.show_interstitial()
		await AdManager.ad_closed

	await get_tree().create_timer(0.9).timeout
	get_tree().change_scene_to_file("res://scenes/main_menu.tscn")

func _input(event: InputEvent) -> void:
	# Check for click/touch to advance to next level
	if event is InputEventMouseButton and event.pressed:
		var hud := get_node_or_null("HUD")
		if hud != null:
			var message = hud.call("get_message")
			if message == "Level Complete! Click to return to menu...":
				get_tree().change_scene_to_file("res://scenes/main_menu.tscn")
