package com.limelight.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import com.limelight.input.Device;
import com.limelight.input.DeviceListener;
import com.limelight.input.gamepad.GamepadComponent;
import com.limelight.input.gamepad.GamepadListener;
import com.limelight.input.gamepad.GamepadMapping;
import com.limelight.input.gamepad.GamepadMapping.Mapping;
import com.limelight.input.gamepad.SourceComponent;
import com.limelight.input.gamepad.SourceComponent.Type;
import com.limelight.settings.GamepadSettingsManager;

/**
 * A frame used to configure the gamepad mappings.
 * @author Diego Waxemberg
 */
public class GamepadConfigFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	private boolean configChanged = false;

	private MappingThread mappingThread;
	private GamepadMapping config;
	private HashMap<Box, Mapping> componentMap;

	/**
	 * Constructs a new config frame. The frame is initially invisible and will <br>
	 * be made visible after all components are built by calling <code>build()</code>
	 */
	public GamepadConfigFrame() {
		super("Gamepad Settings");
		System.out.println("Creating Settings Frame");
		this.setSize(850, 550);
		this.setResizable(false);
		this.setAlwaysOnTop(true);
		config = GamepadSettingsManager.getSettings();

	}

	/**
	 * Builds all components of the config frame and sets the frame visible.
	 */
	public void build() {
		componentMap = new HashMap<Box, Mapping>();

		GridLayout layout = new GridLayout(GamepadComponent.values().length/2 + 1, 2);
		layout.setHgap(60);
		layout.setVgap(3);
		JPanel mainPanel = new JPanel(layout);

		GamepadComponent[] components = GamepadComponent.values();

		for (int i = 0; i < components.length; i++) {

			Mapping mapping = config.get(components[i]);
			if (mapping == null) {
				mapping = config.new Mapping(components[i], false, false);
			}
			Box componentBox = createComponentBox(mapping);

			mainPanel.add(componentBox);

		}

		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		this.setLocation(dim.width/2-this.getSize().width/2, dim.height/2-this.getSize().height/2);
		this.setLayout(new BorderLayout());

		this.getContentPane().add(mainPanel, "Center");
		this.getContentPane().add(Box.createVerticalStrut(20), "North");
		this.getContentPane().add(Box.createVerticalStrut(20), "South");
		this.getContentPane().add(Box.createHorizontalStrut(20), "East");
		this.getContentPane().add(Box.createHorizontalStrut(20), "West");

		this.addWindowListener(createWindowListener());
		this.setVisible(true);
	}

	/*
	 * Creates the box that holds the button and checkboxes
	 */
	private Box createComponentBox(Mapping mapping) {
		Box componentBox = Box.createHorizontalBox();

		JButton mapButton = new JButton();
		JCheckBox invertBox = new JCheckBox("Invert");
		JCheckBox triggerBox = new JCheckBox("Trigger");

		Dimension buttonSize = new Dimension(110, 24);
		mapButton.setMaximumSize(buttonSize);
		mapButton.setMinimumSize(buttonSize);
		mapButton.setPreferredSize(buttonSize);
		mapButton.addActionListener(createMapListener());
		
		setButtonText(mapButton, config.getMapping(mapping.padComp));

		invertBox.setSelected(mapping.invert);
		invertBox.addItemListener(createInvertListener());

		triggerBox.setSelected(mapping.trigger);
		triggerBox.addItemListener(createTriggerListener());
		triggerBox.setToolTipText("If this component should act as a trigger. (one-way axis)");

		componentBox.add(Box.createHorizontalStrut(5));
		componentBox.add(mapping.padComp.getLabel());
		componentBox.add(Box.createHorizontalGlue());
		componentBox.add(mapButton);
		componentBox.add(invertBox);
		componentBox.add(triggerBox);
		componentBox.add(Box.createHorizontalStrut(5));

		componentBox.setBorder(new LineBorder(Color.GRAY, 1, true));

		componentMap.put(componentBox, mapping);

		return componentBox;
	}

	//TODO: make createInvertListener() and createTriggerListener() one method. TOO MUCH COPY PASTA!
	/*
	 * Creates the listener for the invert checkbox
	 */
	private ItemListener createInvertListener() {
		return new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				JCheckBox clicked = (JCheckBox)e.getItem();
				GamepadComponent padComp = GamepadComponent.valueOf(clicked.getName());
				config.get(padComp).invert = (e.getStateChange() == ItemEvent.SELECTED);
				configChanged = true;
			}
		};
	}

	/*
	 * Creates the listener for the trigger checkbox
	 */
	private ItemListener createTriggerListener() {
		return new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				JCheckBox clicked = (JCheckBox)e.getItem();
				GamepadComponent padComp = GamepadComponent.valueOf(clicked.getName());
				config.get(padComp).trigger = (e.getStateChange() == ItemEvent.SELECTED);
				configChanged = true;
			}
		};
	}


	/*
	 * Creates the listener for the window.
	 * It will save configs on exit and restart controller threads
	 */
	private WindowListener createWindowListener() {
		return new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (mappingThread != null && mappingThread.isAlive()) {
					mappingThread.interrupt();
				}
				if (configChanged) {
					updateConfigs();
				}
				dispose();
			}
		};
	}

	/*
	 * Creates the listener for the map button
	 */
	private ActionListener createMapListener() {
		return new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Box toMap = (Box)((JButton)e.getSource()).getParent();

				if (GamepadListener.getInstance().deviceCount() == 0) {
					JOptionPane.showMessageDialog(GamepadConfigFrame.this, "No Gamepad Detected");
					return;
				}

				map(toMap);
			}
		};
	}

	/*
	 * Maps a gamepad component to the clicked component
	 */
	private void map(final Box toMap) {
		if (mappingThread == null || !mappingThread.isAlive()) {
			
			//a little janky, could probably be fixed up a bit
			final JButton buttonPressed = getButton(toMap);
			final Mapping mappingToMap = componentMap.get(toMap);

			buttonPressed.setSelected(true);

			buttonPressed.setText("Select Input");

			mappingThread = new MappingThread(buttonPressed, mappingToMap);
			mappingThread.start();
			
			GamepadListener.getInstance().addDeviceListener(mappingThread);
		}

	}

	/*
	 * Helper method to get the box component that contains the given a mapping
	 */
	private Box getBox(Mapping mapping) {
		for (Entry<Box, Mapping> entry : componentMap.entrySet()) {
			if (entry.getValue() == mapping) {
				return entry.getKey();
			}
		}
		return null;
	}

	/*
	 * Helper method to get the button out of the box component
	 */
	private JButton getButton(Box componentBox) {
		for (java.awt.Component comp : componentBox.getComponents()) {
			if (comp instanceof JButton)
				return (JButton)comp;
		}
		return null;
	}

	/*
	 * Writes the current cofig to the configs on disk.
	 */
	private void updateConfigs() {
		GamepadSettingsManager.writeSettings(config);
	}
	
	private void setButtonText(JButton button, SourceComponent comp) {
		if (comp == null) {
			button.setText("");
		} else {
			button.setText(comp.getType().name() + " " + comp.getId());
		}
	}
	
	private class MappingThread extends Thread implements DeviceListener {
		private SourceComponent newMapping = null;
		private JButton buttonPressed;
		private Mapping mappingToMap; 
		
		public MappingThread(JButton buttonPressed, Mapping mappingToMap) {
			super("Gamepad Mapping Thread");
			this.buttonPressed = buttonPressed;
			this.mappingToMap = mappingToMap;
		}
		
		@Override
		public void run() {
			
			while (newMapping == null) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					setButtonText(buttonPressed, config.getMapping(mappingToMap.padComp));
					GamepadListener.getInstance().removeListener(this);
					return;
				}
			}
			
			Mapping oldConfig = config.get(newMapping);
			if (oldConfig != null) {
				getButton(getBox(oldConfig)).setText("");
			}

			config.insertMapping(mappingToMap, newMapping);

			setButtonText(buttonPressed, newMapping);
			configChanged = true;
			
			GamepadListener.getInstance().removeListener(this);
			
		}
		
		@Override
		public void handleButton(Device device, int buttonId, boolean pressed) {
			if (pressed) {
				newMapping = new SourceComponent(Type.BUTTON, buttonId);
			}
		}

		@Override
		public void handleAxis(Device device, int axisId, float newValue,
				float lastValue) {
			if (newValue > 0.75) {
				newMapping = new SourceComponent(Type.AXIS, axisId);
			}
		}
		
	}
}