import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

public class BeatBox {
    JPanel mainPanel;
    ArrayList<JCheckBox> checkBoxList;
    Sequencer sequencer;
    Sequence sequence;
    Track track;
    JFrame theFrame;
    JList incommingList;
    JTextField userMessage;
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqsMap = new HashMap<String, boolean[]>();


    Sequence mySequence = null;

    String[] instrumentNames = {"Bass Drum", "Closed Hi-Hat", "Open Hi-Hat", "Acoustic Snare", "Crash Cymbal", "Hand Clap",
            "High Tom", "Hi Bongo", "Maracas", "Whistle", "Low Conga", "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo", "Open Hi Conga"};

    int[] instruments = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};// нота инструментов

    public static void main(String[] args) {
        new BeatBox().startUp("00");

    }

    public void startUp (String name){
        userName = name;
        try{
            Socket sock= new Socket("127.0.0.1",42420);
            out = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());
            Thread remote = new Thread(new RemoteReader());
            remote.start();
        } catch (Exception ex) {
            System.out.println("could not connect - you will have to play alone.");
        }
        setUpMidi();
        buidGUI();
    }

    public class RemoteReader implements Runnable {
        boolean[] checkBoxState = null;
        String nameToShow = null;
        Object object = null;

        public void run() {
            try {
                while ((object=in.readObject())!= null);
                System.out.println("got an object from server");
                System.out.println(object.getClass());
                String nameToShow = (String) object;
                checkBoxState = (boolean[])in.readObject();
                otherSeqsMap.put(nameToShow, checkBoxState);
                listVector.add(nameToShow);
                incommingList.setListData(listVector);
            }catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    public class MyListSelectionListner implements ListSelectionListener{
        public void valueChanged(ListSelectionEvent listSelectionEvent){
            if (!listSelectionEvent.getValueIsAdjusting()){
                String selected = (String) incommingList.getSelectedValue();
                if (selected!= null){
                    boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }

    public void changeSequence (boolean[] checkboxState){
        for (int i = 0; i< 256; i++){
            JCheckBox checkBox = (JCheckBox) checkBoxList.get(i);
            if(checkboxState[i]){
                checkBox.setSelected(true);
            } else {
                checkBox.setSelected(false);
            }
        }
    }
    public void buidGUI() {
        theFrame = new JFrame("Cyber BeatBox");
        theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));//граница между панелью и кнопками

        checkBoxList = new ArrayList<JCheckBox>();//массив галочек
        Box buttonBox = new Box(BoxLayout.Y_AXIS);//кнопки вертикально

        JButton start = new JButton("Start");
        start.addActionListener(new MyStartListner());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(new MyStopListner());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(new MyUpTempoListner());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo Down");
        downTempo.addActionListener(new MyDownTempoListner());
        buttonBox.add(downTempo);

        //JButton serializelt = new JButton("serializelt");
        //serializelt.addActionListener(new MySendListner());
        //buttonBox.add(serializelt);

        JButton restore = new JButton("restore");
        restore.addActionListener(new MyReadInListner());
        buttonBox.add(restore);

        JButton sendIt = new JButton("send it");
        restore.addActionListener(new MySendListner());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);

        incommingList = new JList();
        incommingList.addListSelectionListener(new MyListSelectionListner());
        incommingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incommingList);
        buttonBox.add(theList);
        incommingList.setListData(listVector);

        Box nameBox = new Box(BoxLayout.Y_AXIS);// панель с названием инструментов
        for (int i = 0; i < 16; i++) {
            nameBox.add(new Label(instrumentNames[i]));//добавляем название
        }
        background.add(BorderLayout.EAST, buttonBox);//справа функциональные кнопки
        background.add(BorderLayout.WEST, nameBox);//слева название инструментов

        theFrame.getContentPane().add(background);//добавить background на фрейм

        GridLayout grid = new GridLayout(16, 16);// сеточка для галочек 16 на 16
        grid.setVgap(1);// устанавливает вертикальный разрыв
        grid.setHgap(2);// устанавливает горизонтальный разрыв
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);// добавить панельку на фрейм

        for (int i = 0; i < 256; i++) {//состояния полей
            JCheckBox c = new JCheckBox();
            c.setSelected(false);// по умолчанию галочка не стоит
            checkBoxList.add(c);//добавляем в массив
            mainPanel.add(c);// добавляем на панель
        }

        //setUpMidi();

        theFrame.setBounds(50, 50, 300, 300);//начальные координаты, размеры сетки
        theFrame.pack();// группировать относительно середны
        theFrame.setVisible(true);//видимость


    }

    public void setUpMidi() {
        try {
            sequencer = MidiSystem.getSequencer();// получение плеера
            sequencer.open();// открываем плеер
            sequence = new Sequence(Sequence.PPQ, 4);// создаем новую послеовательность
            track = sequence.createTrack();// добавляем трек
            sequencer.setTempoInBPM(120);// темп ударов в минуту
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class MyDownTempoListner implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            float tempoFactor = sequencer.getTempoFactor();// Получаем к-во ударов
            sequencer.setTempoFactor((float) (tempoFactor * .97));//уменьшаем темп
        }
    }

    class MyUpTempoListner implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactor * 1.03));// увеличиваем темп
        }
    }

    public void buildTrackAndStart() {
        ArrayList<Integer> trackList = null;

        sequence.deleteTrack(track);//очищаем трек
        track = sequence.createTrack();// записываем трек

        for (int i = 0; i < 16; i++) {//считываем галочки относительно одного инструента
            trackList = new ArrayList<Integer>();
            int key = instruments[i];

            for (int j = 0; j < 16; j++) {// проходим по тактам
                JCheckBox jc = (JCheckBox) checkBoxList.get(j + (16 * i));//получаем конкретную ячейку
                if (jc.isSelected()) {// если галочка стоит, записываем ее в массив
                    trackList.add(new Integer(key)) ;
                } else {
                    trackList.add(null) ;
                }
            }
            makeTracks(trackList);
            track.add(makeEvent(176, 1, 127, 0, 16));// для регистрации события

        }
        track.add(makeEvent(192, 9, 1, 0, 15));// для измеения инструмента
        try {
            sequencer.setSequence(sequence);//устанавливаем послеовательность
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);//зацикливаем
            sequencer.start();// начинаем проигрывать
            sequencer.setTempoInBPM(120);//ритм
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void makeTracks(ArrayList list) {
        Iterator it = list.iterator();
        for (int i = 0; i < 16; i++) {
            Integer num = (Integer) it.next();

            if (num!= null) {
                int numKey = num.intValue();
                track.add(makeEvent(144, 9, numKey, 100, i));//играть
                track.add(makeEvent(128, 9, numKey, 100, i + 1));//остановить
            }
        }
    }

    public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick) {
        MidiEvent event = null;
        try {
            ShortMessage a = new ShortMessage();//созаем сообщение
            a.setMessage(comd, chan, one, two);// команда, канал, нота, сила нажатия
            event = new MidiEvent(a, tick);//сообщение, его время
        } catch (Exception e) {
            e.printStackTrace();
        }
        return event;
    }

    public class MyStartListner implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            buildTrackAndStart();//записать трек и проигрывать его
        }
    }

    public class MyStopListner implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            sequencer.stop();// остановка
        }
    }

    public class MySendListner implements ActionListener {

        public void actionPerformed(ActionEvent actionEvent) {
            /*boolean[] checkboxState = new boolean[256];
            for (int i = 0; i < 256; i++) {
                JCheckBox check = (JCheckBox) checkBoxList.get(i);
                if (check.isSelected()) {
                    checkboxState[i] = true;
                }
            }

             */
            String messageToSend = null;
            try {

                out.writeObject( userMessage.getText());
                System.out.println("1111");
                out.flush();
                //out.writeObject(checkboxState);
                //FileOutputStream fileOutputStream = new FileOutputStream(new File("Checkbox.ser"));
                //ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
                //objectOutputStream.writeObject(checkboxState);


            } catch (Exception ex) {
                System.out.println("sorry dude. could not send it to the server.");
                ex.printStackTrace();
            }
            userMessage.setText("");
            userMessage.requestFocus();
        }
    }

    public class MyReadInListner implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            boolean[] checkboState = null;
            try {
                FileInputStream fileInputStream = new FileInputStream(new File("Checkbox.ser"));
                ObjectInput input = new ObjectInputStream(fileInputStream);
                checkboState = (boolean[]) input.readObject();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            for (int i = 0; i < 256; i++) {
                JCheckBox check = (JCheckBox) checkBoxList.get(i);
                if (checkboState[i]) {
                    check.setSelected(true);
                } else {
                    check.setSelected(false);
                }
            }

            sequencer.stop();
            buildTrackAndStart();
        }
    }

    public class MyChangeFileName implements ActionListener{
        public void actionPerformed(ActionEvent actionEvent){

        }
    }
}
