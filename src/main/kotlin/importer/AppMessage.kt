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


class AppMessage {
    companion object {
        private val label = JLabel()

        fun createLoginMessage() {
            val loginMessage = "Logue para importar as playlists."
            val closeMessage = "Essa mensagem se fechará automaticamente ao logar, ou você pode encerrar manualmente o app ao clicar em encerrar."

            val frame = JFrame("APP")
            val button = JButton("Encerrar")
            val panel = JPanel()
            val textPanel = JPanel()
            val pane = frame.contentPane

            frame.layout = null
            pane.layout = null

            frame.setSize(400, 300)
            frame.setLocationRelativeTo(null)

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    currentServer.stop(0)
                }
                override fun windowClosing(e: WindowEvent?) {
                    currentServer.stop(0)
                }
            })

            button.addMouseListener(object : MouseListener {
                override fun mousePressed(e: MouseEvent?) {
                    frame.dispose()
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
                (frame.width/2.66).toInt() + insets.left, (frame.height/1.66).toInt() + insets.top,
                size.width, size.height
            )

            textPanel.setBounds(
                50, 50, 300, 500
            )

            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.isVisible = true
            frame.isAlwaysOnTop = true

            app = frame
        }

        fun createImportingMessage() {
            app?.dispose()

            val importingMessage = "Importando..."

            val frame = JFrame("APP")
            val textPanel = JPanel()
            val pane = frame.contentPane

            frame.layout = null
            pane.layout = null

            frame.setSize(400, 300)
            frame.setLocationRelativeTo(null)

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    currentServer.stop(0)
                }
                override fun windowClosing(e: WindowEvent?) {
                    currentServer.stop(0)
                }
            })

            pane.add(textPanel)
            label.text = htmlCenter(importingMessage)
            textPanel.add(label)

            textPanel.setBounds(
                0, 100, 400, 300
            )

            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.isVisible = true
            frame.isAlwaysOnTop = true

            app = frame
        }

        fun createDoneMessage() {
            app?.dispose()

            val frame = JFrame("APP")
            val button = JButton("Encerrar")
            val panel = JPanel()
            val textPanel = JPanel()
            val pane = frame.contentPane

            frame.layout = null
            pane.layout = null

            frame.setSize(400, 300)
            frame.setLocationRelativeTo(null)

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    currentServer.stop(0)
                }
                override fun windowClosing(e: WindowEvent?) {
                    currentServer.stop(0)
                }
            })

            button.addMouseListener(object : MouseListener {
                override fun mousePressed(e: MouseEvent?) {
                    frame.dispose()
                }

                override fun mouseClicked(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseExited(e: MouseEvent?) {}
            })

            pane.add(panel)
            pane.add(textPanel)

            panel.add(button)
            label.text = htmlCenter("Importação concluída. <br><br>" + getFilePath(importFilePath))
            textPanel.add(label)

            val insets = pane.insets
            val size = panel.preferredSize

            panel.setBounds(
                150 + insets.left, 180 + insets.top,
                size.width, size.height
            )

            textPanel.setBounds(
                0, 65, 400, 300
            )

            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.isVisible = true
            frame.isAlwaysOnTop = true

            app = frame
        }

        fun updateMessage(message: String) {
            label.text = htmlCenter(message)
        }

        private fun getFilePath(importFilePath: String): String? {
            val file = File(importFilePath).listFiles()?.firstOrNull { it.name == "$saveWithName.$saveAs" }
            return file?.canonicalPath
        }

        private fun htmlCenter(message: String) = "<html><body><div width='200px' align='center'>$message</div></body></html>"
    }
}