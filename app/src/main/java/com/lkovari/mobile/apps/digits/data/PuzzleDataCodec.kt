package com.lkovari.mobile.apps.digits.data

import com.lkovari.mobile.apps.digits.domain.DailyProgress
import com.lkovari.mobile.apps.digits.domain.Operand
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import com.lkovari.mobile.apps.digits.domain.StageLevel
import com.lkovari.mobile.apps.digits.domain.StagePuzzle
import org.json.JSONArray
import org.json.JSONObject

object PuzzleDataCodec {
    fun serializePuzzleDay(puzzle: PuzzleDay): String {
        val root = JSONObject()
        root.put("day", puzzle.dayEpochMillis)
        root.put("locale", puzzle.locale)
        val stages = JSONArray()
        puzzle.stages.forEach { stage ->
            val stageJson = JSONObject()
            stageJson.put("stageIndex", stage.stageIndex)
            stageJson.put("expectedValue", stage.target)
            val operands = JSONArray()
            stage.operands.forEach { operand -> operands.put(operand.value) }
            stageJson.put("operands", operands)
            stages.put(stageJson)
        }
        root.put("stages", stages)
        return root.toString()
    }

    fun parsePuzzleDay(json: String): PuzzleDay? {
        return try {
            val root = JSONObject(json)
            val dayValue = root.get("day")
            val dayMillis = when (dayValue) {
                is Number -> dayValue.toLong()
                is String -> dayValue.toLongOrNull() ?: return null
                else -> return null
            }
            val locale = root.optString("locale", "")
            val stagesJson = root.getJSONArray("stages")
            val stages = mutableListOf<StagePuzzle>()
            for (i in 0 until stagesJson.length()) {
                val stageJson = stagesJson.getJSONObject(i)
                val operandsJson = stageJson.getJSONArray("operands")
                val operands = mutableListOf<Operand>()
                for (j in 0 until operandsJson.length()) {
                    operands.add(Operand(id = j, value = operandsJson.getInt(j)))
                }
                stages.add(
                    StagePuzzle(
                        stageIndex = stageJson.getInt("stageIndex"),
                        target = stageJson.getInt("expectedValue"),
                        operands = operands
                    )
                )
            }
            PuzzleDay(
                dayEpochMillis = dayMillis,
                locale = locale,
                stages = stages.sortedBy { it.stageIndex }
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serializeProgress(progress: DailyProgress): String {
        val root = JSONObject()
        root.put("day", progress.dayEpochMillis)
        root.put("stageIndex", progress.stageIndex)
        root.put("completed", progress.completed)
        val levels = JSONArray()
        progress.stageLevels.forEach { level ->
            val json = JSONObject()
            json.put("index", level.index)
            json.put("target", level.target)
            json.put("completed", level.completed)
            json.put("selected", level.selected)
            json.put("summary", level.summary)
            levels.put(json)
        }
        root.put("stageLevels", levels)
        val stages = JSONArray()
        progress.stages.forEach { stage ->
            val json = JSONObject()
            json.put("stageIndex", stage.stageIndex)
            json.put("target", stage.target)
            val operands = JSONArray()
            stage.operands.forEach { op ->
                val opJson = JSONObject()
                opJson.put("id", op.id)
                opJson.put("value", op.value)
                opJson.put("disabled", op.disabled)
                operands.put(opJson)
            }
            json.put("operands", operands)
            stages.put(json)
        }
        root.put("stages", stages)
        return root.toString()
    }

    fun parseProgress(raw: String): DailyProgress? {
        return try {
            val root = JSONObject(raw)
            val levelsJson = root.getJSONArray("stageLevels")
            val levels = mutableListOf<StageLevel>()
            for (i in 0 until levelsJson.length()) {
                val json = levelsJson.getJSONObject(i)
                levels.add(
                    StageLevel(
                        index = json.getInt("index"),
                        target = json.getInt("target"),
                        completed = json.getBoolean("completed"),
                        selected = json.optBoolean("selected", false),
                        summary = json.optString("summary", "")
                    )
                )
            }
            val stagesJson = root.getJSONArray("stages")
            val stages = mutableListOf<StagePuzzle>()
            for (i in 0 until stagesJson.length()) {
                val json = stagesJson.getJSONObject(i)
                val operandsJson = json.getJSONArray("operands")
                val operands = mutableListOf<Operand>()
                for (j in 0 until operandsJson.length()) {
                    val opJson = operandsJson.getJSONObject(j)
                    operands.add(
                        Operand(
                            id = opJson.getInt("id"),
                            value = opJson.getInt("value"),
                            disabled = opJson.optBoolean("disabled", false)
                        )
                    )
                }
                stages.add(
                    StagePuzzle(
                        stageIndex = json.getInt("stageIndex"),
                        target = json.getInt("target"),
                        operands = operands
                    )
                )
            }
            DailyProgress(
                dayEpochMillis = root.getLong("day"),
                stageIndex = root.getInt("stageIndex"),
                completed = root.getBoolean("completed"),
                stageLevels = levels,
                stages = stages
            )
        } catch (_: Exception) {
            null
        }
    }
}
