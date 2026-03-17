extends Control

func _ready() -> void:
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

func _on_level1_pressed() -> void:
	# Hide banner before starting level
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 0
	get_tree().change_scene_to_file("res://scenes/main.tscn")

func _on_level2_pressed() -> void:
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 1
	get_tree().change_scene_to_file("res://scenes/main.tscn")

func _on_level3_pressed() -> void:
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 2
	get_tree().change_scene_to_file("res://scenes/main.tscn")

func _on_level4_pressed() -> void:
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 3
	get_tree().change_scene_to_file("res://scenes/main.tscn")

func _on_level5_pressed() -> void:
	AdManager.hide_banner()
	# Set the pending level index and change scene
	var main_script = preload("res://scripts/main.gd")
	main_script.pending_level_index = 4
	get_tree().change_scene_to_file("res://scenes/main.tscn")

func _on_exit_pressed() -> void:
	get_tree().quit()
