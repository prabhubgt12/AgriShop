extends Node

signal ad_loaded(ad_type: String)
signal ad_failed_to_load(ad_type: String, error: String)
signal ad_closed(ad_type: String)

# Production AdMob IDs
const APP_ID := "ca-app-pub-2556604347710668~1526947737"
const BANNER_ID := "ca-app-pub-2556604347710668/2366354288"  # Keep test banner for now
const INTERSTITIAL_ID := "ca-app-pub-2556604347710668/3832347769"
const REWARDED_ID := "ca-app-pub-2556604347710668/9395034749"

var _ad_view: AdView
var _interstitial_ad: InterstitialAd
var _rewarded_ad: RewardedAd

var _interstitial_load_callback := InterstitialAdLoadCallback.new()
var _rewarded_load_callback := RewardedAdLoadCallback.new()
var _interstitial_content_callback := FullScreenContentCallback.new()
var _rewarded_content_callback := FullScreenContentCallback.new()
var _ad_listener := AdListener.new()

func _ready() -> void:
	if ClassDB.class_exists("MobileAds"):
		MobileAds.initialize()
	
	_setup_callbacks()

func _setup_callbacks() -> void:
	# Interstitial callbacks
	_interstitial_load_callback.on_ad_failed_to_load = _on_interstitial_failed_to_load
	_interstitial_load_callback.on_ad_loaded = _on_interstitial_loaded
	_interstitial_content_callback.on_ad_dismissed_full_screen_content = func():
		ad_closed.emit("interstitial")
		_destroy_interstitial()
	_interstitial_content_callback.on_ad_failed_to_show_full_screen_content = func(err: AdError):
		print("Interstitial failed to show: " + err.message)
		ad_failed_to_load.emit("interstitial", err.message)
		_destroy_interstitial()
	_interstitial_content_callback.on_ad_showed_full_screen_content = func():
		pass

	# Rewarded callbacks
	_rewarded_load_callback.on_ad_failed_to_load = _on_rewarded_failed_to_load
	_rewarded_load_callback.on_ad_loaded = _on_rewarded_loaded
	_rewarded_content_callback.on_ad_dismissed_full_screen_content = func():
		ad_closed.emit("rewarded")
		_destroy_rewarded()
	_rewarded_content_callback.on_ad_failed_to_show_full_screen_content = func(err: AdError):
		print("Rewarded ad failed to show: " + err.message)
		ad_failed_to_load.emit("rewarded", err.message)
		_destroy_rewarded()
	_rewarded_content_callback.on_ad_showed_full_screen_content = func():
		pass

	# Banner listener
	_ad_listener.on_ad_loaded = func(_ad):
		ad_loaded.emit("banner")
	_ad_listener.on_ad_failed_to_load = func(error: LoadAdError):
		print("Banner ad failed to load: " + error.message)
		ad_failed_to_load.emit("banner", error.message)
	_ad_listener.on_ad_clicked = func():
		print("Banner clicked")

# ---------- Banner ----------
func load_banner() -> void:
	if _ad_view:
		_ad_view.destroy()
	
	var ad_size := AdSize.get_current_orientation_anchored_adaptive_banner_ad_size(AdSize.FULL_WIDTH)
	_ad_view = AdView.new(BANNER_ID, ad_size, AdPosition.Values.BOTTOM)
	_ad_view.ad_listener = _ad_listener
	_ad_view.load_ad(AdRequest.new())

func show_banner() -> void:
	if _ad_view:
		_ad_view.show()

func hide_banner() -> void:
	if _ad_view:
		_ad_view.hide()

func destroy_banner() -> void:
	if _ad_view:
		_ad_view.destroy()
		_ad_view = null

# ---------- Interstitial ----------
func load_interstitial() -> void:
	InterstitialAdLoader.new().load(INTERSTITIAL_ID, AdRequest.new(), _interstitial_load_callback)

func show_interstitial() -> void:
	if _interstitial_ad:
		_interstitial_ad.full_screen_content_callback = _interstitial_content_callback
		_interstitial_ad.show()

func _destroy_interstitial() -> void:
	if _interstitial_ad:
		_interstitial_ad.destroy()
		_interstitial_ad = null

func _on_interstitial_loaded(ad: InterstitialAd) -> void:
	_interstitial_ad = ad
	ad_loaded.emit("interstitial")

func _on_interstitial_failed_to_load(error: LoadAdError) -> void:
	print("Interstitial ad failed to load: " + error.message)
	ad_failed_to_load.emit("interstitial", error.message)

# ---------- Rewarded ----------
func load_rewarded() -> void:
	RewardedAdLoader.new().load(REWARDED_ID, AdRequest.new(), _rewarded_load_callback)

func show_rewarded() -> void:
	if _rewarded_ad:
		_rewarded_ad.full_screen_content_callback = _rewarded_content_callback
		_rewarded_ad.show()

func _destroy_rewarded() -> void:
	if _rewarded_ad:
		_rewarded_ad.destroy()
		_rewarded_ad = null

func _on_rewarded_loaded(ad: RewardedAd) -> void:
	_rewarded_ad = ad
	ad_loaded.emit("rewarded")

func _on_rewarded_failed_to_load(error: LoadAdError) -> void:
	print("Rewarded ad failed to load: " + error.message)
	ad_failed_to_load.emit("rewarded", error.message)
