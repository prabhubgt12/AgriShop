extends RigidBody2D

func _physics_process(delta):
	# Only sync wheel sprite rotation with physics body rotation
	if has_node("Sprite2D"):
		$Sprite2D.rotation = rotation
		# Center wheel sprite on its physics body
		$Sprite2D.position = Vector2(0, 0)  # Reset to center
