package importer

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JLabel
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.lang.Exception
import kotlin.system.exitProcess

object App {
    private const val appName = "DeezerPlaylistImporter"
    private const val version = "v1.1"
    private lateinit var app: JFrame

    private val label = JLabel()

    fun createLoginMessage() {
        val loginMessage = "Logue para importar as playlists."
        val closeMessage = "Essa mensagem se fechará automaticamente ao logar, ou você pode encerrar manualmente o app ao clicar em encerrar."

        val frame = JFrame("$appName-$version").also { it.setIconImage() }
        val button = JButton("Encerrar")
        val panel = JPanel()
        val textPanel = JPanel()
        val pane = frame.contentPane

        frame.layout = null
        pane.layout = null

        frame.setSize(400, 300)
        frame.setLocationRelativeTo(null)

        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                Server.shutDown()
            }
        })

        button.addMouseListener(object : MouseListener {
            override fun mousePressed(e: MouseEvent?) {
                exitProcess(0)
            }

            override fun mouseClicked(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
        })

        pane.add(panel)
        pane.add(textPanel)

        panel.add(button)
        label.text = htmlCenter("$loginMessage <br><br> $closeMessage")
        textPanel.add(label)

        val insets = pane.insets
        val size = panel.preferredSize

        panel.setBounds(
            150 + insets.left,
            180 + insets.top,
            size.width,
            size.height
        )

        textPanel.setBounds(
            50,
            50,
            300,
            500
        )

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isVisible = true
        frame.isAlwaysOnTop = true

        app = frame
    }

    fun createImportingMessage() {
        app.dispose()

        val importingMessage = "Importando..."

        val frame = JFrame("$appName-$version").also { it.setIconImage() }
        val textPanel = JPanel()
        val pane = frame.contentPane

        frame.layout = null
        pane.layout = null

        frame.setSize(400, 300)
        frame.setLocationRelativeTo(null)

        pane.add(textPanel)
        label.text = htmlCenter(importingMessage)
        textPanel.add(label)

        textPanel.setBounds(
            0,
            100,
            400,
            300
        )

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isVisible = true
        frame.isAlwaysOnTop = true

        app = frame
    }

    fun createDoneMessage() {
        app.dispose()

        val frame = JFrame("$appName-$version").also { it.setIconImage() }
        val textPanel = JPanel()
        val pane = frame.contentPane

        frame.layout = null
        pane.layout = null

        label.text = htmlCenter("Importação concluída. <br><br>" + getFilesPath())

        val filesQuantity = label.text.split(' ').filter { it == "<br>" }.size
        val pathLength = label.text.split(' ').last { it.contains(saveWithName) }.length

        frame.setSize((pathLength * 7.25).toInt(), 300 + (filesQuantity * if (filesQuantity <= 15) 3 else 11))
        frame.setLocationRelativeTo(null)

        pane.add(textPanel)
        textPanel.add(label)

        textPanel.setBounds(
            0,
            (65 - (filesQuantity * 4)).let { if (it < 4) 4 else it },
            -15 + frame.size.width,
            100 + frame.size.height
        )

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isVisible = true
        frame.isAlwaysOnTop = true

        app = frame
    }

    fun updateMessage(message: String) {
        label.text = htmlCenter(message)
    }

    private fun getFilesPath(): String? {
        val files = File(exportFilePath).listFiles()?.filter { it.name.startsWith(saveWithName) && it.extension == saveAs }
            ?.sortedBy { it.name.substring(saveWithName.length, it.name.lastIndexOf('.')).let { fileNumber -> if (fileNumber.isBlank()) "1" else fileNumber }.toInt() }

        val filesPath = files?.map { "${it.canonicalPath} <br> " }
        return filesPath?.reduce { acc, s -> "$acc $s" }
    }

    private fun htmlCenter(message: String) = "<html><body><div width='200px' align='center'>$message</div></body></html>"

    private fun JFrame.setIconImage() {
        try {
            val imageFile = this@App.javaClass.classLoader.getResource("importDeezer.png")
            val image: BufferedImage = ImageIO.read(imageFile)
            this.iconImage = image
        } catch (e: Exception) {}
    }
}