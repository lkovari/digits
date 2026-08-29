# LKovariHome Digits / Numbers — known issues

Inventory of bugs and algorithm problems in
`/Users/kovarilaszlo/src/angular/LKovariHome/src/app/digits/`.
Angular sources were **not** modified; Android Digits implements corrected behavior.

## Critical correctness bugs

### 1. Assignment written as comparison when mapping Firestore puzzle to stage UI
- **File:** `digits-game.component.ts` (~line 216)
- **Code:** `stageLevel.value == puzzleDataStage.expectedValue;`
- **Issue:** Comparison is a no-op; intended assignment was `=`. Relies on later `setupStages()` to repair values.

### 2. Infinite loop risk in constructive generation (`continue` without progress)
- **File:** `generate-game-parameters.ts` — `calculateResult`
- **Issue:** On invalid subtraction/division, `continue` does not increment `ix`, so random invalid ops can spin forever.
- **Android fix:** Cap attempts; choose only operators valid for the chosen pair.

### 3. Infinite loop risk in unique number sampling
- **File:** `generate-unique-number.ts`
- **Issue:** Rejection sampling with no attempt limit. If more unique draws are requested than the range allows, the loop never exits.
- **Android fix:** Attempt limit; regenerate the whole set on failure.

### 4. Outer target-band retry can hang
- **File:** `generate-game-parameters.ts` (~260–271)
- **Issue:** `while (minResult > stageResult || stageResult > maxResult)` has no max retries.
- **Android fix:** Cap retries (hundreds); fall back or regenerate operands.

### 5. Stage 0 generates 7 numbers but only uses 6
- **File:** `generate-game-parameters.ts` case `0`
- **Issue:** Seven `generateNumber` calls; then only indices `0..5` after sort are used.
- **Android fix:** Generate exactly six operands.

### 6. Sort by target leaves stale `stageIndex`
- **File:** `generate-game-parameters.ts` — `generateStageNumbers`
- **Issue:** Stages are sorted by `result` ascending but each keeps its original `stageIndex`. Array order and `stageIndex` diverge; Firestore round-trips keyed by `stageIndex` can desync from UI order.
- **Android fix:** After sort, reindex `stageIndex` to `0..4`.

### 7. Subtraction validity mismatch
- **Files:** `generate-game-parameters.ts` (`operand1 > operand2`) vs `evaluate-arythmetic-operation.ts` (`operandA >= operandB`)
- **Issue:** Generator forbids equal operands for subtraction while playtime evaluation allows `A - B` when `A === B`.
- **Android fix:** Align on `A >= B`.

## Gameplay / history bugs

### 8. Undo pollutes solution / share history
- **File:** `game-arithmetic-operations.component.ts` — `onOperatorButtonClick` for `OPERATOR_REV`
- **Issue:** Undo pushes a revert entry into `operationHistory`, so “Completed!” / clipboard text includes bogus undo steps.
- **Android fix:** Undo only pops the board stack; never append to solution steps.

### 9. `clearHistory()` does not clear `operationHistory`
- **File:** `game-arithmetic-operations.component.ts`
- **Issue:** After a stage completes, only board undo history is cleared. Next stage summaries can include prior stage ops.
- **Android fix:** Clear both histories on stage advance / solve.

### 10. `createSummaryOfTHeOperations` destructively empties the stack
- **File:** parent digits game component (summary helpers)
- **Issue:** Formatting pops the stack after peek-based formatting paths, leaving history unusable afterward.

## Daily puzzle / Firebase issues

### 11. Stale cookie path regenerates without reading Firestore
- **File:** `digits-game.component.ts` `ngOnInit`
- **Issue:** TODO notes “check first in the DB!” but stale cookie regenerates locally and upserts, splitting the locale’s daily puzzle across clients.
- **Android fix:** Prefer Firestore same-day puzzle before generate; DataStore only for same calendar day.

### 12. Full-collection `snapshotChanges` never unsubscribed
- **File:** `digits-game.component.ts`
- **Issue:** Cold-start subscription can re-apply puzzles on remote changes and leaks.
- **Android fix:** One-shot load on bootstrap.

### 13. Race: generate locally before Firestore returns
- **Issue:** UI can briefly show a local puzzle then swap when DB arrives.
- **Android fix:** Await lookup before presenting playable board (with offline fallback).

### 14. Locale matching / `getAll()` cost
- **File:** `numbers-firestore.service.ts` + game component
- **Issue:** Loads entire `puzzledata` collection then filters in memory.
- **Android note:** Same one-shot get for v1; document ideally queried by `locale` later.

## UI / UX / code quality

### 15. Clipboard / share broken (acknowledged TODO)
- **File:** `digits-game.component.ts` header TODO
- **Issue:** “share copy to clipboard does not works”.
- **Android:** System ShareSheet (`Intent.ACTION_SEND`).

### 16. Stage toolbar clicks do not change stage
- **File:** `stage-communication.service.ts` + parent
- **Issue:** Parent only logs stage updates; cannot jump stages (sequential play only). Documented as intentional sequential UX on Android as well.

### 17. ADD operator bias in random op picker
- **File:** `generate-game-parameters.ts` — `choiceRandomOperation`
- **Issue:** Index range `0..4` with cases `0..3`; case `4` falls through to ADD → ADD ~40%.
- **Android fix:** Uniform choice among valid ops for the pair.

### 18. Dead animation / console noise
- **Files:** `operand-button-animation.ts`, arithmetic component
- **Issue:** `operandButtonAnimation` never set; animation unused. Frequent `console.log` on clicks.

### 19. `StageLevel` constructor typo
- **File:** stage-level model
- **Issue:** `this,this.summary = summary` (comma expression).

### 20. Model / typing inconsistencies
- **Issues:** `IGameOperand` declared as a class in an `*.interface.ts` file; `GameOperator` model omits `id` required by interface; lodash listed in package but unused in digits.

## Algorithm improvements chosen for Android

1. Uniform / validity-aware operator selection during generation.
2. Hard caps on unique-number sampling, combine cycles, and target-band retries.
3. Exactly six operands per stage; reindex after difficulty sort.
4. Clean undo + solution histories.
5. Firestore day match preferred over local regenerate; DataStore mirrors cookie for same day only.
6. Subtraction rule `A >= B` everywhere.
