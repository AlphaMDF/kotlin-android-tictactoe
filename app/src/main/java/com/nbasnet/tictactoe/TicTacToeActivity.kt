package com.nbasnet.tictactoe

import android.databinding.DataBindingUtil
import android.databinding.ObservableInt
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.nbasnet.extensions.activity.*
import com.nbasnet.helpers.AppPreferences
import com.nbasnet.helpers.FontManager
import com.nbasnet.tictactoe.ai.AIFactory
import com.nbasnet.tictactoe.ai.AIPlayerTypes
import com.nbasnet.tictactoe.ai.IPlayGameAI
import com.nbasnet.tictactoe.controllers.TicTacToeGameController
import com.nbasnet.tictactoe.databinding.TictactoeGameBinding
import com.nbasnet.tictactoe.models.*
import kotlinx.android.synthetic.main.tictactoe_game.*
import java.util.*

class TicTacToeActivity : AppCompatActivity() {
    private lateinit var _fullGameInfo: GameInfo
    private lateinit var _gameController: TicTacToeGameController

    private var _gridRow: Int = 3
    private var _startPlayer: Int = 1
    private lateinit var buttonList: List<BtnAreaInfo>
    private lateinit var _aITaskHandler: Handler
    private lateinit var _sharePreference: AppPreferences
    lateinit var customFont: Typeface

    private val DEBUG_MODE = false
    /**
     * View oncreate function
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _sharePreference = AppPreferences(this)
        _startPlayer = _sharePreference.startPlayer
        //set up the window mode
        fullScreenMode()
        setContentView(R.layout.tictactoe_game)

        customFont = FontManager.getCustomFontForLanguage(assets, _sharePreference.selectedLanguage)
        title = resources.getString(R.string.title_game_room)

        val playersInfo = intent.getBundleExtra(APP_PAYLOAD)
        loadBindPlayerAndGameInfos(playersInfo)

        //set label for the indication of current player
        setWinnerCurrentPlayerLabels()

        //List to hold the info about all the playable region
        buttonList = getAllPlayAreas(gameArea)
        /**
         * Add event handlers for all the button
         */
        buttonList.forEach {
            val btnInfo = it
            it.button.setOnClickListener {
                if (_gameController.canHumanPlay) {
                    val btnAreaInfo = PlayAreaInfo(btnInfo.row, btnInfo.col)
                    selectArea(it, btnAreaInfo)
                } else {
                    failToast(resources.getString(R.string.error_wait_for_turn))
                }
            }
        }

        thinkingContents.visibility = View.INVISIBLE
        //set up play again button
        btnPlayAgain.visibility = View.INVISIBLE
        btnPlayAgain.setOnClickListener {
            val animSlideDown = YoYo.with(Techniques.SlideOutDown)
                    .duration(1000)
            animSlideDown.playOn(it)
            animSlideDown.playOn(player1Active)
            animSlideDown.playOn(player2Active)
            YoYo.with(Techniques.ZoomOut)
                    .duration(1000)
                    .onEnd {
                        //toggle next player
                        _sharePreference.startPlayer = if (_startPlayer == 1) 2 else 1

                        refreshActivity(true)
                    }
                    .playOn(gameArea)
        }

        setCustomFont()
        startGame()
    }

    override fun onStart() {
        super.onStart()
        //animate gameboard
        val board = gameArea.background
        when (board) {
            is Animatable -> board.start()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        //roll in the game area table
        YoYo.with(Techniques.SlideInUp)
                .duration(1000)
                .playOn(btnPlayAgain)
    }

    /**
     * Load player, full gameinfo and create gamecontroller and bind fullGameInfo to the main content view
     */
    private fun loadBindPlayerAndGameInfos(playersInfo: Bundle): Unit {
        //retrieve the users info from the payload passed to the view
        val inP1Name = playersInfo.getString(PLAYER1)
        val inP2Name = playersInfo.getString(PLAYER2)
        val isP1AI = playersInfo.getBoolean(PLAYER1_AI)
        val isP2AI = playersInfo.getBoolean(PLAYER2_AI)
        _gridRow = playersInfo.getInt(GRID_ROW, 3)

        if (isP1AI || isP2AI) _aITaskHandler = Handler()

        //generate player classes and create the game info and controller classes
        val _player1 = getPlayer(1, inP1Name, isP1AI)
        val _player2 = getPlayer(2, inP2Name, isP2AI)
        _fullGameInfo = GameInfo(
                PlayerGameInfo(
                        _player1,
                        ObservableInt(_sharePreference.player1WinsCount)
                ),
                PlayerGameInfo(
                        _player2,
                        ObservableInt(_sharePreference.player2WinsCount)
                )
        )
        _gameController = TicTacToeGameController(_player1, _player2, _gridRow, _startPlayer)
        //Bind the game info with the tictactoe_game view
        val binding: TictactoeGameBinding = DataBindingUtil.setContentView(this, R.layout.tictactoe_game)
        binding.gameInfo = _fullGameInfo
    }

    /**
     * Get all playable button info
     */
    private fun getAllPlayAreas(viewGroup: ViewGroup): List<BtnAreaInfo> {
        val playAreas = arrayListOf<BtnAreaInfo>()
        findButtons(viewGroup, playAreas, 0)
        return playAreas.toList()
    }

    /**
     * Get all the buttons from the view group and fill it up in fillButtonList variable
     */
    private fun findButtons(viewGroup: ViewGroup, fillButtonList: ArrayList<BtnAreaInfo>, viewRow: Int) {
        var row = viewRow
        for (col in 0..viewGroup.childCount) {
            val child = viewGroup.getChildAt(col)

            if (child is ViewGroup) {
                row++
                findButtons(child, fillButtonList, row)
            } else if (child is Button) {
                val actualCol = col + 1
                val btnInfo = BtnAreaInfo(viewRow, actualCol, child)
                fillButtonList.add(btnInfo)
            }
        }
    }

    /**
     * Stub method to generated and return player
     */
    private fun getPlayer(playerNo: Int, name: String? = null, isAI: Boolean = false): Player {
        val aiController: IPlayGameAI? = if (isAI) AIFactory.getAIPlayer(AIPlayerTypes.EASY) else null

        return if (playerNo == 1)
            Player(name ?: "Player1", R.drawable.tictactoe_cross_anim, R.color.green_500, aiController)
        else
            Player(name ?: "Player2", R.drawable.tictactoe_circle_anim, R.color.blue_500, aiController)
    }

    private fun setCustomFont(): Unit {
        btnPlayAgain.typeface = customFont
        labelWinner.typeface = customFont
        labelWinnerBanner.typeface = customFont
        labelWinPlayer1.typeface = customFont
        labelWinPlayer2.typeface = customFont
    }

    /**
     * Start game and let ai start playing if first player is ai
     */
    private fun startGame(): Unit {
        checkForAIAndLetAIPlay(_gameController.currentPlayer)
    }

    /**
     * Perform the task for region selected with given area
     */
    private fun selectArea(area: View, areaInfo: PlayAreaInfo) {
        //play the next round and update the view with the proper values
        _gameController.playNextRound(
                selectedAreaInfo = areaInfo,
                onSuccess = { it, (_, message) ->
                    val customUserSource = it.currentPlayer.getDrawableSymbol(resources, theme)
                    if (customUserSource == null) {
                        area.setBackgroundResource(it.currentPlayer.symbolResource)
                    } else {
                        area.background = customUserSource
                        when (customUserSource) {
                            is Animatable -> customUserSource.start()
                        }
                    }

                    if (DEBUG_MODE) toast(message, Toast.LENGTH_SHORT)
                },
                onFail = { _, (_, message) ->
                    failToast(message, Toast.LENGTH_SHORT)
                },
                onPlayerChange = { _, nextPlayer: Player ->
                    setWinnerCurrentPlayerLabels()

                    checkForAIAndLetAIPlay(nextPlayer)
                },
                onGameFinished = {
                    btnPlayAgain.visibility = View.VISIBLE
                    YoYo.with(Techniques.SlideInUp)
                            .duration(1000)
                            .playOn(btnPlayAgain)

                    if (it.currentPlayer.isWinner) {
                        successToast("Game over winner: ${it.currentPlayer.name}", Toast.LENGTH_SHORT)

                        //set the winscores
                        if (_gameController.player1.isWinner) {
                            YoYo.with(Techniques.Bounce)
                                    .duration(1000)
                                    .playOn(labelWinPlayer1)
                            _fullGameInfo.player1Info.win.set(_fullGameInfo.player1Info.win.get() + 1)
                            _sharePreference.player1WinsCount = _fullGameInfo.player1Info.win.get()
                        } else if (_gameController.player2.isWinner) {
                            YoYo.with(Techniques.Bounce)
                                    .duration(1000)
                                    .playOn(labelWinPlayer1)
                            _fullGameInfo.player2Info.win.set(_fullGameInfo.player2Info.win.get() + 1)
                            _sharePreference.player2WinsCount = _fullGameInfo.player2Info.win.get()
                        }
                    } else {
                        successToast("Game Drawn!!")
                    }
                }
        )
    }

    /**
     * Check if the current player is ai and let if play
     */
    private fun checkForAIAndLetAIPlay(currentPlayer: Player): Unit {
        if (currentPlayer.isAI && currentPlayer.aIController != null && !_gameController.isGameFinished) {
            letAIPlayNextRound(
                    currentPlayer.aIController,
                    2000,
                    beforePlay = {
                        thinkingContents.visibility = View.VISIBLE
                        val thinkAnim = imageThinking.drawable
                        when (thinkAnim) {
                            is Animatable -> thinkAnim.start()
                        }
                        YoYo.with(Techniques.BounceInDown)
                                .duration(700)
                                .playOn(imageThinking)
                    },
                    afterPlay = {
                        thinkingContents.visibility = View.INVISIBLE
                    }
            )
        }
    }

    /**
     * Let the ai player play the next round
     */
    private fun letAIPlayNextRound(
            aIController: IPlayGameAI,
            delayInMs: Long,
            beforePlay: () -> Unit = {},
            afterPlay: () -> Unit = {}
    ) {
        _gameController.canHumanPlay = false
        beforePlay()
        _aITaskHandler.postDelayed({
            //give control to the ai to play next round here
            val nextAreaInfo = aIController.getNextPlayAreaInfo(_gameController)

            //get the button
            val btnToClick = buttonList.firstOrNull {
                it.col == nextAreaInfo.column && it.row == nextAreaInfo.row
            }

            //call the buttons on click
            _gameController.canHumanPlay = true
            btnToClick?.button?.callOnClick()
            afterPlay()
        }, delayInMs)
    }

    /**
     * Define animations
     */
    val slideOutLeftAnim: YoYo.AnimationComposer = YoYo.with(Techniques.SlideOutLeft).duration(700)
    val slideInLeftAnim: YoYo.AnimationComposer = YoYo.with(Techniques.SlideInLeft).duration(700)
    val slideOutRightAnim: YoYo.AnimationComposer = YoYo.with(Techniques.SlideOutRight).duration(700)
    val slideInRightAnim: YoYo.AnimationComposer = YoYo.with(Techniques.SlideInRight).duration(700)
    /**
     * Method to update the current player indicating icon and winner name
     */
    private fun setWinnerCurrentPlayerLabels(): Unit {

        if (_gameController.isGameFinished) {
            if (_gameController.isDrawGame()) {
                labelWinnerBanner.text = resources.getString(R.string.label_draw)
            } else {
                labelWinnerBanner.text = resources.getString(R.string.label_winner)
                labelWinner.text = _gameController.currentPlayer.name
                YoYo.with(Techniques.Tada)
                        .duration(1000)
                        .repeat(3)
                        .playOn(labelWinner)

                YoYo.with(Techniques.Wobble)
                        .duration(1000)
                        .repeat(3)
                        .playOn(if (_gameController.isCurrentPlayer1()) player1Active else player2Active)

                winningRegionAnimations(_gameController.currentPlayer.winningRow())
            }
        } else {
            if (_gameController.isCurrentPlayer1()) {
                slideOutLeftAnim.playOn(player2Active)
                slideInRightAnim.playOn(player1Active)
            } else {
                slideOutRightAnim.playOn(player1Active)
                slideInLeftAnim.playOn(player2Active)
            }
        }
    }

    /**
     * Handles winning region animation
     */
    private fun winningRegionAnimations(winningRegionInfo: FullRegionInfo): Unit {
        val waveAnimation = YoYo.with(Techniques.Wave).duration(4000)

        if (winningRegionInfo.regionType == RegionType.FORWARD_DIAGONAL) {
            buttonList.forEach {
                if (_gameController.frontDiagonalRows.contains(PlayAreaInfo(it.row, it.col))) {
                    waveAnimation.playOn(it.button)
                }
            }
        } else if (winningRegionInfo.regionType == RegionType.BACK_DIAGONAL) {
            buttonList.forEach {
                if (it.col == it.row) {
                    waveAnimation.playOn(it.button)
                }
            }
        } else if (winningRegionInfo.regionType == RegionType.COL) {
            buttonList.forEach {
                if (it.col == winningRegionInfo.rowCol) {
                    waveAnimation.playOn(it.button)
                }
            }
        } else if (winningRegionInfo.regionType == RegionType.ROW) {
            buttonList.forEach {
                if (it.row == winningRegionInfo.rowCol) {
                    waveAnimation.playOn(it.button)
                }
            }
        }
    }
}

data class BtnAreaInfo(val row: Int, val col: Int, val button: Button)