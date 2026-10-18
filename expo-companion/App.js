import React, { useEffect, useState } from 'react';
import { Alert, FlatList, KeyboardAvoidingView, Platform, SafeAreaView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = 'hector_job_tracker_v1';
const STATUSES = ['در حال بررسی', 'آماده‌سازی رزومه', 'ارسال شد', 'مصاحبه', 'پیشنهاد شغلی', 'رد شد', 'متوقف شد'];
const EMPTY = { company: '', role: '', link: '', deadline: '', nextAction: '', notes: '' };

export default function App() {
  const [jobs, setJobs] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const saved = await AsyncStorage.getItem(STORAGE_KEY);
        if (saved) setJobs(JSON.parse(saved));
      } catch {
        Alert.alert('خطا', 'خواندن سوابق ذخیره‌شده ناموفق بود.');
      } finally { setLoaded(true); }
    })();
  }, []);

  useEffect(() => {
    if (loaded) AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(jobs)).catch(() =>
      Alert.alert('خطا', 'ذخیره اطلاعات انجام نشد.')
    );
  }, [jobs, loaded]);

  const update = (key, value) => setForm(current => ({ ...current, [key]: value }));
  const reset = () => { setForm(EMPTY); setEditingId(null); };

  const save = () => {
    if (!form.company.trim() || !form.role.trim()) {
      Alert.alert('اطلاعات ناقص', 'نام شرکت و عنوان شغل را وارد کن.');
      return;
    }
    if (editingId) {
      setJobs(current => current.map(job => job.id === editingId ? { ...job, ...form, updatedAt: new Date().toISOString() } : job));
    } else {
      setJobs(current => [{ ...form, id: String(Date.now()), status: STATUSES[0], createdAt: new Date().toISOString() }, ...current]);
    }
    reset();
  };

  const edit = job => {
    setEditingId(job.id);
    setForm({ company: job.company || '', role: job.role || '', link: job.link || '', deadline: job.deadline || '', nextAction: job.nextAction || '', notes: job.notes || '' });
  };

  const changeStatus = id => {
    const job = jobs.find(item => item.id === id);
    if (!job) return;
    const next = (STATUSES.indexOf(job.status) + 1) % STATUSES.length;
    setJobs(current => current.map(item => item.id === id ? { ...item, status: STATUSES[next] } : item));
  };

  const remove = id => Alert.alert('حذف سابقه', 'این درخواست شغلی حذف شود؟', [
    { text: 'انصراف', style: 'cancel' },
    { text: 'حذف', style: 'destructive', onPress: () => setJobs(current => current.filter(item => item.id !== id)) }
  ]);

  const field = (key, label, multiline = false) => (
    <TextInput key={key} accessibilityLabel={label} placeholder={label} placeholderTextColor="#7D8797"
      value={form[key]} onChangeText={value => update(key, value)} multiline={multiline}
      autoCapitalize={key === 'link' ? 'none' : 'sentences'} keyboardType={key === 'link' ? 'url' : 'default'}
      style={[styles.input, multiline && styles.multiline]} />
  );

  const renderJob = ({ item }) => (
    <View style={styles.card}>
      <Text style={styles.jobTitle}>{item.role}</Text>
      <Text style={styles.company}>{item.company}</Text>
      <Text style={styles.status}>وضعیت: {item.status}</Text>
      {!!item.deadline && <Text style={styles.detail}>مهلت: {item.deadline}</Text>}
      {!!item.nextAction && <Text style={styles.detail}>اقدام بعدی: {item.nextAction}</Text>}
      {!!item.link && <Text style={styles.detail}>پیوند: {item.link}</Text>}
      {!!item.notes && <Text style={styles.detail}>یادداشت: {item.notes}</Text>}
      <View style={styles.actions}>
        <TouchableOpacity style={styles.smallButton} onPress={() => changeStatus(item.id)}><Text style={styles.buttonText}>تغییر وضعیت</Text></TouchableOpacity>
        <TouchableOpacity style={styles.smallButton} onPress={() => edit(item)}><Text style={styles.buttonText}>ویرایش</Text></TouchableOpacity>
        <TouchableOpacity style={[styles.smallButton, styles.deleteButton]} onPress={() => remove(item.id)}><Text style={styles.buttonText}>حذف</Text></TouchableOpacity>
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor="#101827" />
      <KeyboardAvoidingView style={styles.safe} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <FlatList data={jobs} keyExtractor={item => item.id} renderItem={renderJob} contentContainerStyle={styles.content}
          ListHeaderComponent={<View>
            <Text style={styles.title}>پیگیری درخواست‌های شغلی</Text>
            <Text style={styles.subtitle}>نسخهٔ مستقل آزمایشی هیکتور</Text>
            <View style={styles.form}>
              <Text style={styles.sectionTitle}>{editingId ? 'ویرایش درخواست' : 'ثبت درخواست جدید'}</Text>
              {field('company', 'نام شرکت یا سازمان')}
              {field('role', 'عنوان شغل')}
              {field('link', 'پیوند آگهی (اختیاری)')}
              {field('deadline', 'مهلت درخواست (اختیاری)')}
              {field('nextAction', 'اقدام بعدی (اختیاری)')}
              {field('notes', 'یادداشت‌ها (اختیاری)', true)}
              <TouchableOpacity style={styles.primaryButton} onPress={save}><Text style={styles.primaryText}>{editingId ? 'ذخیره تغییرات' : 'ثبت درخواست'}</Text></TouchableOpacity>
              {!!editingId && <TouchableOpacity style={styles.cancelButton} onPress={reset}><Text style={styles.cancelText}>لغو ویرایش</Text></TouchableOpacity>}
            </View>
            <Text style={styles.sectionTitle}>سوابق ثبت‌شده ({jobs.length})</Text>
            <Text style={styles.notice}>اطلاعات فعلاً فقط روی همین دستگاه ذخیره می‌شود؛ این نسخه هنوز رمزگذاری اختصاصی، ورود کاربر یا همگام‌سازی ابری ندارد. اطلاعات حساس وارد نکن.</Text>
            {jobs.length === 0 && <Text style={styles.empty}>هنوز درخواستی ثبت نشده است.</Text>}
          </View>} />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#101827' },
  content: { padding: 16, paddingBottom: 36 },
  title: { color: '#F4F7FB', fontSize: 25, fontWeight: '800', textAlign: 'right', marginTop: 18 },
  subtitle: { color: '#AEB8C8', fontSize: 14, textAlign: 'right', marginTop: 6, marginBottom: 18 },
  form: { backgroundColor: '#1B2638', borderRadius: 16, padding: 14, marginBottom: 22 },
  sectionTitle: { color: '#F4F7FB', fontSize: 18, fontWeight: '700', textAlign: 'right', marginBottom: 12, marginTop: 8 },
  input: { backgroundColor: '#F5F7FA', color: '#172033', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 12, marginBottom: 10, textAlign: 'right', fontSize: 15 },
  multiline: { minHeight: 86, textAlignVertical: 'top' },
  primaryButton: { backgroundColor: '#3B82F6', borderRadius: 10, padding: 14, alignItems: 'center', marginTop: 4 },
  primaryText: { color: '#FFFFFF', fontWeight: '700', fontSize: 16 },
  cancelButton: { padding: 10, alignItems: 'center' },
  cancelText: { color: '#D3DCE8' },
  card: { backgroundColor: '#1B2638', borderRadius: 14, padding: 14, marginTop: 12 },
  jobTitle: { color: '#FFFFFF', fontSize: 18, fontWeight: '700', textAlign: 'right' },
  company: { color: '#B9C7DB', fontSize: 15, textAlign: 'right', marginTop: 4, marginBottom: 8 },
  status: { color: '#93C5FD', textAlign: 'right', marginBottom: 6 },
  detail: { color: '#D8E0EC', textAlign: 'right', marginTop: 4, lineHeight: 21 },
  actions: { flexDirection: 'row-reverse', flexWrap: 'wrap', gap: 8, marginTop: 12 },
  smallButton: { backgroundColor: '#334155', borderRadius: 8, paddingVertical: 9, paddingHorizontal: 12 },
  deleteButton: { backgroundColor: '#9F3039' },
  buttonText: { color: '#FFFFFF', fontWeight: '600' },
  notice: { color: '#FCD9A6', backgroundColor: '#47351E', borderRadius: 10, padding: 12, textAlign: 'right', lineHeight: 22, marginBottom: 10 },
  empty: { color: '#AEB8C8', textAlign: 'center', padding: 24 }
});