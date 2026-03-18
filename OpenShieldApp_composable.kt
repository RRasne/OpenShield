// MainActivity.kt içindeki OpenShieldApp composable'ını bu versiyonla değiştir.
// Diğer her şey (renkler, tab'lar, bileşenler) aynı kalır.

@Composable
fun OpenShieldApp(
    viewModel: MainViewModel = hiltViewModel()
) {
    var activeTab by remember { mutableStateOf(Tab.HOME) }
    var isProtectionOn by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val spamNumbers    by viewModel.spamNumbers.collectAsState()
    val whitelist      by viewModel.whitelist.collectAsState()
    val blockedLog     by viewModel.blockedLog.collectAsState()
    val pendingReviews by viewModel.pendingReviews.collectAsState()

    val onAddSpam:         (String, String) -> Unit = { n, l -> viewModel.addSpam(n, l) }
    val onRemoveSpam:      (String) -> Unit         = { n    -> viewModel.removeSpam(n) }
    val onAddWhitelist:    (String, String) -> Unit = { n, l -> viewModel.addWhitelist(n, l) }
    val onRemoveWhitelist: (String) -> Unit         = { n    -> viewModel.removeWhitelist(n) }
    val onClearHistory:    () -> Unit               = { viewModel.clearHistory() }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.RECEIVE_SMS] == true
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    Tab.HOME -> HomeTab(
                        isOn            = isProtectionOn,
                        hasPermission   = hasPermission,
                        spamCount       = spamNumbers.size,
                        blockedCount    = blockedLog.size,
                        onToggle        = { isProtectionOn = it },
                        onRequestPermission = {
                            val perms = buildList {
                                add(Manifest.permission.RECEIVE_SMS)
                                add(Manifest.permission.READ_SMS)
                                if (Build.VERSION.SDK_INT >= 33)
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    )
                    Tab.BLACKLIST  -> BlacklistTab(spamNumbers, onAddSpam, onRemoveSpam)
                    Tab.WHITELIST  -> WhitelistTab(whitelist, onAddWhitelist, onRemoveWhitelist)
                    Tab.LOG        -> LogTab(blockedLog, onClearHistory) { sender ->
                        onAddSpam(sender, "Engellenenlerden eklendi")
                    }
                }
            }

            BottomNavBar(activeTab = activeTab, onTabChange = { activeTab = it })
        }

        // ── Şüpheli SMS İnceleme Dialog'u ────────────────────────────────────
        // Uygulama açıldığında pending_review tablosunda kayıt varsa göster.
        // Kullanıcı karar verene kadar kapanmaz (dışarı tıklama devre dışı).
        val currentPending = pendingReviews.firstOrNull()
        if (currentPending != null) {
            SuspiciousReviewDialog(
                pending   = currentPending,
                remaining = pendingReviews.size,
                onSpam    = { viewModel.resolveReview(currentPending, isSpam = true) },
                onNotSpam = { viewModel.resolveReview(currentPending, isSpam = false) }
            )
        }
    }
}
