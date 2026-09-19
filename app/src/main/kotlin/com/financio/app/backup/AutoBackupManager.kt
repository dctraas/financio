package com.financio.app.backup

import android.app.backup.BackupManager
import android.content.Context
import com.financio.core.backup.BackupSerializer
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.DebtRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * "Automatische cloud-backup" without a Google Drive API, an OAuth client, or any server of our
 * own to run - all of which fase 1's "no backend, no network permission" architecture (see
 * AndroidManifest.xml's own doc comment) rules out setting up. Android's built-in Auto
 * Backup/device-transfer already does exactly this for free, tied to the Google account already
 * on the user's device: the OS periodically copies whatever backup_rules.xml/
 * data_extraction_rules.xml whitelist into that account's storage, and restores it automatically
 * right after a fresh install, before the app is even opened.
 *
 * The one thing that can never go through that path is [FinancioDatabase] itself: it's SQLCipher-
 * encrypted with a passphrase wrapped by an AndroidKeystore key
 * ([com.financio.app.data.local.DatabasePassphraseProvider]), and that key is hardware-backed -
 * it never leaves this device, not even to a factory-reset version of the same device. Restoring
 * the raw database file without it would just leave an undecryptable file. So instead, this
 * writes the exact same portable JSON [BackupSerializer.exportAll] already produces for the
 * manual "Volledige back-up exporteren" button to its own whitelisted file, and
 * [restoreIfEmpty] - run once at every app startup - loads that snapshot back in through the same
 * additive-only [BackupRestoreUseCase] the manual "Bestand importeren" flow uses, the first time
 * this install has no real data of its own yet.
 */
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val debtRepository: DebtRepository,
    private val backupRestoreUseCase: BackupRestoreUseCase,
) {
    private val snapshotFile: File
        get() = File(context.filesDir, "$AUTO_BACKUP_DIR/$SNAPSHOT_FILE_NAME").also { it.parentFile?.mkdirs() }

    /** Run periodically by [AutoBackupWorker]. [BackupManager.dataChanged] is only a hint - the OS still decides when it's actually convenient (Wi-Fi, charging, idle) to run a real backup pass. */
    suspend fun writeSnapshot() {
        val json = BackupSerializer.exportAll(
            accounts = accountRepository.observeAccounts().first(),
            categories = categoryRepository.observeCategories().first(),
            rules = categoryRepository.observeRules().first(),
            transactions = transactionRepository.observeAllTransactions().first(),
            splitsByTransactionId = transactionRepository.observeAllSplits().first(),
            budgets = budgetRepository.observeAllBudgets().first(),
            savingsGoals = savingsGoalRepository.observeGoals().first(),
            debts = debtRepository.observeDebts().first(),
        )
        snapshotFile.writeText(json)
        BackupManager(context).dataChanged()
    }

    /**
     * Only ever restores into a genuinely empty install - accounts and transactions are the two
     * things every real usage of this app has, so both being empty is "nothing to lose" even in
     * the unlikely case this runs more than once. Never touches an install that already has its
     * own data, even if Android also restored an older snapshot alongside it.
     */
    suspend fun restoreIfEmpty() {
        if (!snapshotFile.exists()) return
        val hasOwnData = accountRepository.observeAccounts().first().isNotEmpty() || transactionRepository.observeAllTransactions().first().isNotEmpty()
        if (hasOwnData) return
        backupRestoreUseCase.restore(snapshotFile.readText())
    }

    companion object {
        private const val AUTO_BACKUP_DIR = "auto_backup"
        private const val SNAPSHOT_FILE_NAME = "financio-backup.json"
    }
}
