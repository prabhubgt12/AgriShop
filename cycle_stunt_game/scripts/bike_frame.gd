extends RigidBody2D

@export var stunt_torque = 80000.0  # Moderate speed for better control
@export var drive_force := 1500.0  # Increased much more for speed
@export var max_upward_velocity := 1800.0
@export var max_angular_velocity := 16.0
@export var jump_impulse := 5500.0
@export var jump_cooldown := 0.35

# Physics damping and limits
var max_upward_clamp = -1200.0

var _score := 0
var _distance_score := 0
var _last_x_position := 0.0
var _was_in_air: bool = false  # Track if bike was in air
var _air_rot_accum: float = 0.0  # Accumulated rotation in air
var _air_last_rot: float = 0.0  # Last rotation when in air
var _stunt_score_accum: float = 0.0  # Stunt bonus accumulator
var _jump_cooldown_left := 0.0
var _auto_jump_cooldown_left := 0.0
var _is_level_complete: bool = false  # Track level completion
var _is_pedaling := false  # Track if currently pedaling for sound

func _ready() -> void:
	# Set physics properties
	angular_damp = 4.0
	physics_material_override.bounce = 0.0
	physics_material_override.friction = 1.0
	continuous_cd = RigidBody2D.CCD_MODE_CAST_SHAPE
	
	_score = 0
	_distance_score = 0
	_last_x_position = global_position.x

func _update_hud_score():
	var hud := get_tree().current_scene.get_node_or_null("HUD") if get_tree().current_scene != null else null
	if hud != null and hud.has_method("set_score"):
		hud.call("set_score", _score + _distance_score)

func _physics_process(delta):
	if _is_level_complete:
		return
	if _jump_cooldown_left > 0.0:
		_jump_cooldown_left = maxf(0.0, _jump_cooldown_left - delta)
	if _auto_jump_cooldown_left > 0.0:
		_auto_jump_cooldown_left = maxf(0.0, _auto_jump_cooldown_left - delta)
	
	# Clamp max upward velocity
	if linear_velocity.y < -1200:
		linear_velocity.y = -1200
	
	var hud := get_tree().current_scene.get_node_or_null("HUD") if get_tree().current_scene != null else null
	var accelerate_state := false
	var brake_state := false
	var stunt_state := false
	
	if hud != null and hud.has_method("get_global_action_state"):
		accelerate_state = hud.call("get_global_action_state", "accelerate")
		brake_state = hud.call("get_global_action_state", "brake")
		stunt_state = hud.call("get_global_action_state", "stunt")

	var bike := get_parent()
	var front_wheel: RigidBody2D = bike.get_node_or_null("FrontWheel") if bike != null else null
	var back_wheel: RigidBody2D = bike.get_node_or_null("BackWheel") if bike != null else null
	var in_air := true
	if front_wheel != null and back_wheel != null:
		in_air = (front_wheel.get_contact_count() == 0 and back_wheel.get_contact_count() == 0)

	var current_x := global_position.x
	if current_x > _last_x_position:
		var distance := current_x - _last_x_position
		_distance_score += int(distance * 0.1)  # 0.1 points per unit traveled
		_last_x_position = current_x
	
	_update_hud_score()

	if accelerate_state and not _is_level_complete:
		# Reduce torque when airborne
		if in_air:
			apply_central_force(Vector2(drive_force * 0.4, 0))
		else:
			apply_central_force(Vector2(drive_force, 0))
		# Start pedal sound if not already playing
		if not _is_pedaling:
			_is_pedaling = true
			var audio_manager := get_tree().current_scene.get_node_or_null("SimpleAudioManager")
			if audio_manager and audio_manager.has_method("play_pedal"):
				audio_manager.call("play_pedal")
	else:
		# Stop pedal sound when accelerate released or level completed
		if _is_pedaling:
			_is_pedaling = false
			var audio_manager := get_tree().current_scene.get_node_or_null("SimpleAudioManager")
			if audio_manager and audio_manager.has_method("stop_pedal"):
				audio_manager.call("stop_pedal")
			
	if brake_state and not _is_level_complete:
		apply_central_force(Vector2(-drive_force * 0.5, 0))
		# Stop pedal sound when braking
		if _is_pedaling:
			_is_pedaling = false
			var audio_manager := get_tree().current_scene.get_node_or_null("SimpleAudioManager")
			if audio_manager and audio_manager.has_method("stop_pedal"):
				audio_manager.call("stop_pedal")

	if front_wheel != null and back_wheel != null:
		var speed := linear_velocity.length()
		var jump_force := 0.0

		if speed > 150:  # Only when moving fast enough
			# Check bike position for known jump points
			var bike_x := global_position.x

			if bike_x > 950 and bike_x < 1250:  # First jump area
				jump_force = speed * 0.22
			elif bike_x > 1550 and bike_x < 1850:  # Second jump area
				jump_force = speed * 0.22
			elif bike_x > 2950 and bike_x < 3250:  # Third jump area
				jump_force = speed * 0.22
			elif bike_x > 4150 and bike_x < 4450:  # Fourth jump area
				jump_force = speed * 0.22
			# Also check rotation for general jumps
			elif rotation < -0.05:  # Going downhill
				jump_force = speed * 0.22

		if jump_force > 0:
			apply_central_impulse(Vector2(0, -jump_force))

	if (Input.is_key_pressed(KEY_SPACE) or Input.is_action_pressed("jump")) and not in_air and _jump_cooldown_left <= 0.0:
		apply_central_impulse(Vector2(0, -jump_impulse))
		_jump_cooldown_left = jump_cooldown

	if in_air and not _was_in_air:
		_was_in_air = true
		_air_rot_accum = 0.0
		_air_last_rot = rotation
		_stunt_score_accum = 0.0
	elif not in_air and _was_in_air:
		_was_in_air = false
		var rot_diff := rotation - _air_last_rot
		_air_rot_accum += rot_diff
		var full_flips := int(abs(_air_rot_accum) / (PI * 2))
		if full_flips > 0:
			_score += full_flips * 100
			_update_hud_score()
		# Restart pedal sound when landing if accelerate is still held
		if accelerate_state and not _is_level_complete and not _is_pedaling:
			_is_pedaling = true
			var audio_manager := get_tree().current_scene.get_node_or_null("SimpleAudioManager")
			if audio_manager and audio_manager.has_method("play_pedal"):
				audio_manager.call("play_pedal")
		_air_rot_accum = 0.0
		_air_last_rot = 0.0

	if in_air:
		var d := rotation - _air_last_rot
		d = wrapf(d, -PI, PI)
		_air_rot_accum += d
		_air_last_rot = rotation

		if stunt_state:
			# Reduce torque when airborne
			if in_air:
				apply_torque(stunt_torque * 0.4)
			else:
				apply_torque(stunt_torque)
			_stunt_score_accum += delta
			if _stunt_score_accum >= 0.2:
				var bonus := int(floor(_stunt_score_accum / 0.2)) * 20  
				_stunt_score_accum = fmod(_stunt_score_accum, 0.2)
				_score += bonus
				_update_hud_score()
			# Clamp velocity and angular velocity
			if linear_velocity.y < -max_upward_velocity:
				linear_velocity.y = -max_upward_velocity
			if absf(angular_velocity) > max_angular_velocity:
				angular_velocity = signf(angular_velocity) * max_angular_velocity

func stop_bike():
	# Stop pedal sound
	if _is_pedaling:
		_is_pedaling = false
		var audio_manager := get_tree().current_scene.get_node_or_null("SimpleAudioManager")
		if audio_manager and audio_manager.has_method("stop_pedal"):
			audio_manager.call("stop_pedal")
	
	linear_velocity = Vector2(0, 0)
	angular_velocity = 0.0
	_is_level_complete = true
	linear_velocity = Vector2.ZERO
	angular_velocity = 0.0
	freeze_mode = RigidBody2D.FREEZE_MODE_STATIC
	call_deferred("set_freeze", true)
	var bike_parent = get_parent()
	if bike_parent != null:
		var front_wheel := bike_parent.get_node_or_null("FrontWheel")
		if front_wheel != null:
			front_wheel.linear_velocity = Vector2.ZERO
			front_wheel.angular_velocity = 0.0
			front_wheel.freeze_mode = RigidBody2D.FREEZE_MODE_STATIC
			front_wheel.call_deferred("set_freeze", true)
		var back_wheel := bike_parent.get_node_or_null("BackWheel")
		if back_wheel != null:
			back_wheel.linear_velocity = Vector2.ZERO
			back_wheel.angular_velocity = 0.0
			back_wheel.freeze_mode = RigidBody2D.FREEZE_MODE_STATIC
			back_wheel.call_deferred("set_freeze", true)
	freeze = false
	linear_velocity = Vector2.ZERO
	angular_velocity = 0.0
	var bike := get_parent()
	if bike != null:
		var front_wheel := bike.get_node_or_null("FrontWheel")
		if front_wheel != null:
			front_wheel.freeze_mode = RigidBody2D.FREEZE_MODE_KINEMATIC
			front_wheel.freeze = false
			front_wheel.linear_velocity = Vector2.ZERO
			front_wheel.angular_velocity = 0.0
		var back_wheel := bike.get_node_or_null("BackWheel")
		if back_wheel != null:
			back_wheel.freeze_mode = RigidBody2D.FREEZE_MODE_KINEMATIC
			back_wheel.freeze = false
			back_wheel.linear_velocity = Vector2.ZERO
			back_wheel.angular_velocity = 0.0
	# Create a simple flash effect by changing the bike's modulate
	var tween = create_tween()
	
	# First flash
	tween.tween_property(self, "modulate", Color.WHITE, 0.1)
	tween.tween_property(self, "modulate", Color(1, 1, 1, 1), 0.3)
	
	# Delay between flashes
	tween.tween_interval(0.5)
	
	# Second flash
	tween.tween_property(self, "modulate", Color.WHITE, 0.1)
	tween.tween_property(self, "modulate", Color(1, 1, 1, 1), 0.3)
