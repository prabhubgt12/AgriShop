extends Node2D

signal game_over
signal level_complete

func _ready():
	# Tag all StaticBody2D nodes in this level as ground.
	for child in get_children():
		if child is StaticBody2D:
			child.add_to_group("ground")

	# Crash detection: Frame must have contact_monitor enabled.
	if has_node("Bike/Frame"):
		$Bike/Frame.body_entered.connect(_on_frame_collision)

	# Finish detection (optional per level)
	if has_node("Finish"):
		$Finish.body_entered.connect(_on_finish_entered)

func _on_frame_collision(body):
	if body.is_in_group("ground"):
		game_over.emit()

func _on_finish_entered(body):
	# Only complete when the bike touches the finish.
	if body != null and body.is_in_group("bike"):
		if has_node("Finish"):
			$Finish.set_deferred("monitoring", false)
			$Finish.set_deferred("monitorable", false)
		if has_node("Bike/Frame"):
			var frame = $Bike/Frame
			if frame != null and frame.has_method("stop_bike"):
				frame.call("stop_bike")
		level_complete.emit()

func _input(event):
	if Input.is_action_just_pressed("restart"):
		game_over.emit()
