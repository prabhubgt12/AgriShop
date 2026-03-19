extends Control

func _ready() -> void:
	print("=== MAIN MENU _ready() STARTED ===")
	
	# Check if SimpleAudioManager exists and is properly loaded
	var audio_mgr = get_node_or_null("SimpleAudioManager")
	if audio_mgr:
		print("✓ SimpleAudioManager node found in scene")
		if audio_mgr.has_method("play_click"):
			print("✓ play_click method available")
			
			# Test if sound file can be loaded
			var test_sound = load("res://assets/sounds/click.wav")
			if test_sound:
				print("✓ click.wav sound file loaded successfully")
			else:
				print("✗ click.wav sound file NOT found or corrupted")
		else:
			print("✗ play_click method NOT available")
	else:
		print("✗ SimpleAudioManager node NOT found in scene")
	
	# Load and show banner ad on main menu
	AdManager.load_banner()
	AdManager.show_banner()
	
	# Make menu buttons mobile-friendly
	var level1_btn = $MenuContainer/Level1Button
	var level2_btn = $MenuContainer/Level2Button
	var level3_btn = $MenuContainer/Level3Button
	var level4_btn = $MenuContainer/Level4Button
	var level5_btn = $MenuContainer/Level5Button
	var exit_btn = $MenuContainer/ExitButton
	
	# Increase font size for mobile
	level1_btn.add_theme_font_size_override("font_size", 28)
	level2_btn.add_theme_font_size_override("font_size", 28)
	level3_btn.add_theme_font_size_override("font_size", 28)
	level4_btn.add_theme_font_size_override("font_size", 28)
	level5_btn.add_theme_font_size_override("font_size", 28)
	exit_btn.add_theme_font_size_override("font_size", 28)
	
	# Set sizes using size property
	level1_btn.size = Vector2(300, 80)
	level2_btn.size = Vector2(300, 80)
	level3_btn.size = Vector2(300, 80)
	level4_btn.size = Vector2(300, 80)
	level5_btn.size = Vector2(300, 80)
	exit_btn.size = Vector2(300, 80)
	
	print("=== MAIN MENU _ready() COMPLETE ===")

func _on_button_click() -> void:
	# Play click sound immediately when any button is pressed
	print("=== BUTTON CLICK DETECTED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling it...")
			audio_manager.call("play_click")
			print("play_click called successfully")
		else:
			print("ERROR: play_click method not found!")
	else:
		print("ERROR: SimpleAudioManager not found!")
	print("=== BUTTON CLICK HANDLED ===")

func _on_level1_pressed() -> void:
	print("=== LEVEL 1 BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Level 1")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	# Hide banner before starting level
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 0
	get_tree().change_scene_to_file("res://scenes/main.tscn")
	print("=== LEVEL 1 HANDLING COMPLETE ===")

func _on_level2_pressed() -> void:
	print("=== LEVEL 2 BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Level 2")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 1
	get_tree().change_scene_to_file("res://scenes/main.tscn")
	print("=== LEVEL 2 HANDLING COMPLETE ===")

func _on_level3_pressed() -> void:
	print("=== LEVEL 3 BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Level 3")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 2
	get_tree().change_scene_to_file("res://scenes/main.tscn")
	print("=== LEVEL 3 HANDLING COMPLETE ===")

func _on_level4_pressed() -> void:
	print("=== LEVEL 4 BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Level 4")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 3
	get_tree().change_scene_to_file("res://scenes/main.tscn")
	print("=== LEVEL 4 HANDLING COMPLETE ===")

func _on_level5_pressed() -> void:
	print("=== LEVEL 5 BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Level 5")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 4
	get_tree().change_scene_to_file("res://scenes/main.tscn")
	print("=== LEVEL 5 HANDLING COMPLETE ===")

func _on_exit_pressed() -> void:
	print("=== EXIT BUTTON PRESSED ===")
	var audio_manager := get_node_or_null("SimpleAudioManager")
	if audio_manager:
		print("SimpleAudioManager found")
		if audio_manager.has_method("play_click"):
			print("play_click method found, calling...")
			audio_manager.call("play_click")
			print("✓ Click sound played for Exit")
		else:
			print("✗ play_click method not found!")
	else:
		print("✗ SimpleAudioManager not found!")
	
	get_tree().quit()
	print("=== EXIT HANDLING COMPLETE ===")
